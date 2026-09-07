package me.whereareiam.anvil.platform.velocity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Updates Velocity TOML using typed values while keeping runtime-owned routing authoritative.
 */
final class VelocityConfiguration {
	private static final Set<String> OWNED = Set.of("bind", "motd", "show-max-players", "online-mode",
			"force-key-authentication", "player-info-forwarding-mode", "forwarding-secret-file",
			"announce-forge", "config-version", "servers", "try");
	private final TomlMapper toml = new TomlMapper();

	void write(MinecraftProxy proxy, PlatformContext context) throws IOException {
		var forwarding = context.getForwarding();
		if (forwarding.getMode() == ForwardingMode.NONE || forwarding.getSecret() == null)
			throw new PlatformException("Velocity requires negotiated forwarding settings and a secret");

		Path file = context.getWorkDirectory().resolve("velocity.toml");
		ObjectNode config = read(file);
		for (var setting : proxy.getSettings().entrySet()) {
			applySetting(config, setting.getKey(), setting.getValue());
		}

		config.put("config-version", "2.8");
		config.put("bind", context.getBindAddress() + ":" + context.getPort());
		config.put("motd", "Anvil: " + proxy.getName());
		config.put("show-max-players", 20);
		config.put("online-mode", proxy.isOnlineMode());
		config.put("force-key-authentication", proxy.isOnlineMode());
		config.put("player-info-forwarding-mode", forwarding.getMode().name().toLowerCase(Locale.ROOT));
		config.put("forwarding-secret-file", "forwarding.secret");
		config.put("announce-forge", false);

		ObjectNode servers = config.putObject("servers");
		for (String server : proxy.getServers()) {
			servers.put(server, context.getProcessAddresses().get(server).getHostString() + ":" + context.getProcessAddresses().get(server).getPort());
		}

		servers.putArray("try").add(proxy.getDefaultServer());
		config.withObject("/forced-hosts");
		toml.writeValue(file.toFile(), config);
		Files.writeString(context.getWorkDirectory().resolve("forwarding.secret"),
				forwarding.getSecret(), StandardCharsets.UTF_8);
	}

	private JsonNode literal(String key, String value) {
		try {
			JsonNode document = toml.readTree("value = " + value);
			if (document.size() != 1 || !document.has("value")) {
				throw new PlatformException("Velocity setting '" + key + "' must contain exactly one TOML value");
			}

			return document.get("value");
		} catch (JsonProcessingException exception) {
			throw new PlatformException("Velocity setting '" + key + "' must be a TOML literal; quote string values", exception);
		}
	}

	private void applySetting(ObjectNode config, String key, String value) throws IOException {
		literal(key, value);

		ObjectNode patch = (ObjectNode) toml.readTree(key + " = " + value);
		if (patch.size() != 1) throw new PlatformException("Velocity setting must assign exactly one key: " + key);

		String root = patch.fieldNames().next();
		if (OWNED.contains(root)) throw new PlatformException("Velocity setting '" + key + "' is owned by Anvil runtime configuration");
		merge(config, patch);
	}

	private void merge(ObjectNode target, ObjectNode patch) {
		for (var field : patch.properties()) {
			if (field.getValue() instanceof ObjectNode nested && target.get(field.getKey()) instanceof ObjectNode existing) {
				merge(existing, nested);
				continue;
			}

			target.set(field.getKey(), field.getValue());
		}
	}

	private ObjectNode read(Path file) throws IOException {
		if (!Files.exists(file)) return toml.createObjectNode();
		JsonNode document = toml.readTree(file.toFile());
		if (document == null) return toml.createObjectNode();
		if (document instanceof ObjectNode object) return object;

		throw new PlatformException("Velocity configuration must contain a TOML table: " + file);
	}
}
