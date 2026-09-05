package me.whereareiam.anvil.platform.bungeecord;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Writes typed BungeeCord routing into an existing YAML mapping.
 */
final class BungeeCordConfiguration {
	private static final Set<String> OWNED = Set.of("online_mode", "ip_forward", "listeners", "servers",
			"network_compression_threshold");

	private final YAMLMapper yaml = YAMLMapper.builder()
			.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
			.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();

	void write(MinecraftProxy proxy, PlatformContext context) throws IOException {
		if (context.getForwarding().getMode() != ForwardingMode.LEGACY)
			throw new PlatformException("BungeeCord requires legacy forwarding");
		for (String key : proxy.getSettings().keySet())
			if (OWNED.contains(key))
				throw new PlatformException("BungeeCord setting '" + key + "' is owned by Anvil runtime configuration");

		Path file = context.getWorkDirectory().resolve("config.yml");
		ObjectNode config = read(file);
		for (var setting : proxy.getSettings().entrySet())
			config.set(setting.getKey(), yaml.readTree(setting.getValue()));

		config.put("online_mode", proxy.isOnlineMode());
		config.put("ip_forward", true);
		config.put("log_commands", true);
		config.put("network_compression_threshold", 256);
		config.putArray("listeners").add(listener(proxy, context));
		ObjectNode servers = config.putObject("servers");
		for (String server : proxy.getServers())
			servers.putObject(server).put("address", context.getBindAddress() + ":" + context.getProcessPorts().get(server))
					.put("motd", "Anvil server " + server).put("restricted", false);

		yaml.writeValue(file.toFile(), config);
	}

	private ObjectNode listener(MinecraftProxy proxy, PlatformContext context) {
		ObjectNode listener = yaml.createObjectNode();
		listener.put("host", context.getBindAddress() + ":" + context.getPort());
		listener.put("motd", "Anvil: " + proxy.getName());
		listener.put("max_players", 20);
		listener.putArray("priorities").add(proxy.getDefaultServer());
		listener.put("force_default_server", true);
		listener.put("ping_passthrough", false);
		listener.put("query_enabled", false);
		listener.put("proxy_protocol", false);
		listener.put("tab_list", "GLOBAL_PING");
		listener.put("tab_size", 60);
		listener.put("bind_local_address", true);

		return listener;
	}

	private ObjectNode read(Path file) throws IOException {
		if (!Files.exists(file)) return yaml.createObjectNode();
		JsonNode document = yaml.readTree(file.toFile());
		if (document == null) return yaml.createObjectNode();
		if (document instanceof ObjectNode object) return object;

		throw new PlatformException("BungeeCord configuration must contain a YAML mapping: " + file);
	}
}
