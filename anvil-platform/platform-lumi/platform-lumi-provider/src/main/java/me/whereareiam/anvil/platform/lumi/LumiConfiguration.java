package me.whereareiam.anvil.platform.lumi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Writes Lumi's {@code settings.yml} structurally before each start. Unrelated values in the file are kept,
 * Anvil's defaults apply unless a server setting replaces them, and the listener, the accepted protocol, the
 * authentication mode and the console language, which readiness matches, always come from the scenario. A workspace
 * holding a {@code server.properties} is refused, because Lumi converts that file over {@code settings.yml}. A server
 * setting names a dotted path into the file, such as {@code world.spawn-protection}, and gives a YAML value.
 */
@RequiredArgsConstructor
final class LumiConfiguration {
	private static final String FILE = "settings.yml";
	private static final String LEGACY_FILE = "server.properties";
	// The readiness pattern matches the English start message.
	private static final String LANGUAGE = "eng";
	private static final Set<String> OWNED = Set.of(
			"general.server-ip",
			"general.server-port",
			"general.language",
			"general.multiversion.min-protocol",
			"general.multiversion.max-protocol",
			"network.xbox-auth",
			"network.rcon.enable"
	);

	private final @NotNull LumiReleases releases;
	private final YAMLMapper yaml = YAMLMapper.builder()
			.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
			.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
			.build();

	void write(@NotNull MinecraftServer server, @NotNull PlatformContext context) throws IOException {
		if (context.getForwarding().getMode() != ForwardingMode.NONE)
			throw new PlatformException("Lumi does not support identity forwarding");

		MinecraftVersion version = server.nativeVersion();
		if (version == null)
			throw new PlatformException("Lumi server '" + server.getName() + "' must declare its Minecraft version");
		int protocol = releases.protocol(version);

		Path legacy = context.getWorkDirectory().resolve(LEGACY_FILE);
		if (Files.exists(legacy))
			throw new PlatformException("Lumi server '" + server.getName() + "' has a " + LEGACY_FILE + ", which Lumi "
					+ "converts over the listener, protocol and authentication Anvil writes; remove it and give its "
					+ "values as " + FILE + " server settings instead: " + legacy);

		Path file = context.getWorkDirectory().resolve(FILE);
		ObjectNode settings = read(file);
		set(settings, "general.motd", yaml.getNodeFactory().textNode("Anvil: " + server.getName()));
		set(settings, "world.spawn-protection", yaml.getNodeFactory().numberNode(0));
		set(settings, "network.enable-query", yaml.getNodeFactory().booleanNode(false));
		set(settings, "bstats.enable", yaml.getNodeFactory().booleanNode(false));
		for (Map.Entry<String, String> setting : server.getSettings().entrySet())
			apply(settings, setting.getKey(), setting.getValue());

		set(settings, "general.server-ip", yaml.getNodeFactory().textNode(context.getBindAddress()));
		set(settings, "general.language", yaml.getNodeFactory().textNode(LANGUAGE));
		set(settings, "general.server-port", yaml.getNodeFactory().numberNode(context.getPort()));
		set(settings, "general.multiversion.min-protocol", yaml.getNodeFactory().numberNode(protocol));
		set(settings, "general.multiversion.max-protocol", yaml.getNodeFactory().numberNode(protocol));
		set(settings, "network.xbox-auth", yaml.getNodeFactory().booleanNode(server.isOnlineMode()));
		set(settings, "network.rcon.enable", yaml.getNodeFactory().booleanNode(false));
		yaml.writeValue(file.toFile(), settings);
	}

	private void apply(ObjectNode settings, String key, String value) {
		if (OWNED.contains(key))
			throw new PlatformException("Lumi setting '" + key + "' is owned by Anvil runtime configuration");

		JsonNode node;
		try {
			node = yaml.readTree(value);
		} catch (JsonProcessingException exception) {
			throw new PlatformException("Lumi setting '" + key + "' must be a YAML value", exception);
		}

		set(settings, key, node == null || node.isMissingNode() ? yaml.getNodeFactory().nullNode() : node);
	}

	private void set(ObjectNode settings, String key, JsonNode value) {
		String[] path = key.split("\\.");
		ObjectNode parent = settings;
		for (int index = 0; index < path.length - 1; index++) {
			JsonNode child = parent.get(path[index]);
			if (child != null && !(child instanceof ObjectNode))
				throw new PlatformException("Lumi setting '" + key + "' crosses '" + path[index] + "', which is not a mapping");

			parent = child == null ? parent.putObject(path[index]) : (ObjectNode) child;
		}

		parent.set(path[path.length - 1], value);
	}

	private ObjectNode read(Path file) throws IOException {
		if (!Files.exists(file)) return yaml.createObjectNode();

		JsonNode document = yaml.readTree(file.toFile());
		if (document == null || document.isMissingNode()) return yaml.createObjectNode();
		if (document instanceof ObjectNode object) return object;

		throw new PlatformException("Lumi configuration must contain a YAML mapping: " + file);
	}
}
