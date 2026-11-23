package me.whereareiam.anvil.platform.bungeecord;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;

import java.io.IOException;
import java.util.Set;

/**
 * Writes typed BungeeCord routing into an existing YAML mapping.
 */
final class BungeeCordConfiguration {
	private static final String CONFIGURATION = "config.yml";
	private static final Set<String> OWNED = Set.of("online_mode", "ip_forward", "listeners", "servers",
			"network_compression_threshold");

	private final YAMLMapper yaml = YAMLMapper.builder()
			.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
			.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();

	void write(MinecraftProxy proxy, PlatformContext context) throws IOException {
		if (context.forwarding().mode() != ForwardingMode.LEGACY)
			throw new PlatformException("BungeeCord requires legacy forwarding");

		for (String key : proxy.settings().keySet())
			if (OWNED.contains(key))
				throw new PlatformException("BungeeCord setting '" + key + "' is owned by Anvil runtime configuration");

		ProcessFiles files = context.files();
		ObjectNode config = read(files);
		for (var setting : proxy.settings().entrySet())
			config.set(setting.getKey(), yaml.readTree(setting.getValue()));

		config.put("online_mode", proxy.onlineMode());
		config.put("ip_forward", true);
		config.put("log_commands", true);
		config.put("network_compression_threshold", 256);
		config.putArray("listeners").add(listener(proxy, context));
		ObjectNode servers = config.putObject("servers");
		for (String server : proxy.servers())
			servers.putObject(server).put("address", context.processAddresses().get(server).getHostString() + ":"
					+ context.processAddresses().get(server).getPort())
					.put("motd", "Anvil server " + server).put("restricted", false);

		files.write(CONFIGURATION, yaml.writeValueAsString(config));
	}

	private ObjectNode listener(MinecraftProxy proxy, PlatformContext context) {
		ObjectNode listener = yaml.createObjectNode();
		listener.put("host", context.bindAddress() + ":" + context.port());
		listener.put("motd", "Anvil: " + proxy.name());
		listener.put("max_players", 20);
		listener.putArray("priorities").add(proxy.defaultServer());
		listener.put("force_default_server", true);
		listener.put("ping_passthrough", false);
		listener.put("query_enabled", false);
		listener.put("proxy_protocol", false);
		listener.put("tab_list", "GLOBAL_PING");
		listener.put("tab_size", 60);
		listener.put("bind_local_address", true);

		return listener;
	}

	private ObjectNode read(ProcessFiles files) throws IOException {
		if (!files.exists(CONFIGURATION)) return yaml.createObjectNode();

		JsonNode document = yaml.readTree(files.read(CONFIGURATION));
		if (document == null || document.isMissingNode()) return yaml.createObjectNode();
		if (document instanceof ObjectNode object) return object;

		throw new PlatformException("BungeeCord configuration must contain a YAML mapping: " + CONFIGURATION);
	}
}
