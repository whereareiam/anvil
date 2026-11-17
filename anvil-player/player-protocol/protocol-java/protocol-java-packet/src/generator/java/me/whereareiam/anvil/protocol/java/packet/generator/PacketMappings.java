package me.whereareiam.anvil.protocol.java.packet.generator;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.type.ConnectionState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the names of the packets a server sends from minecraft-data's {@code protocol.json}: for each connection
 * state, the {@code toClient} packet type's {@code name} mapper, which maps each packet ID, as a hexadecimal string,
 * to minecraft-data's name of the packet.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PacketMappings {
	/**
	 * Reads the names of one protocol's clientbound packets in each state the file has; a state it lacks, such as
	 * configuration before Minecraft 1.20.2, has none.
	 *
	 * @param context what the file is, named in failures, such as its path and protocol
	 * @param protocol the file's content
	 * @return the names by state, each list indexed by packet ID
	 * @throws IllegalStateException when the file breaks the expected shape, or its IDs leave a gap or repeat
	 */
	public static @NotNull Map<ConnectionState, List<String>> clientbound(@NotNull String context, @NotNull JsonNode protocol) {
		Map<ConnectionState, List<String>> names = new EnumMap<>(ConnectionState.class);
		for (ConnectionState state : ConnectionState.values()) {
			JsonNode section = protocol.path(state.name().toLowerCase(Locale.ROOT));
			if (section.isMissingNode()) continue;

			names.put(state, names(context + " " + section(state), mapper(context + " " + section(state), section)));
		}
		if (!names.containsKey(ConnectionState.PLAY)) throw new IllegalStateException(context + " has no play packets");

		return names;
	}

	private static String section(ConnectionState state) {
		return state.name().toLowerCase(Locale.ROOT) + ".toClient";
	}

	/**
	 * Finds the mappings of the packet type: {@code ["container", [{"name": "name", "type": ["mapper", {"mappings":
	 * {...}}]}, ...]]}.
	 */
	private static JsonNode mapper(String context, JsonNode section) {
		JsonNode packet = section.path("toClient").path("types").path("packet");
		for (JsonNode field : packet.path(1)) {
			if (!"name".equals(field.path("name").asText())) continue;

			JsonNode type = field.path("type");
			if (!"mapper".equals(type.path(0).asText())) break;
			JsonNode mappings = type.path(1).path("mappings");
			if (mappings.isObject()) return mappings;
		}

		throw new IllegalStateException(context + " has no packet name mapper");
	}

	private static List<String> names(String context, JsonNode mappings) {
		Map<Integer, String> byId = new TreeMap<>();
		for (Map.Entry<String, JsonNode> entry : mappings.properties()) {
			int id = id(context, entry.getKey());
			String name = entry.getValue().asText("");
			if (name.isBlank()) throw new IllegalStateException(context + " names packet " + entry.getKey() + " blankly");
			if (byId.put(id, name) != null) throw new IllegalStateException(context + " maps packet ID " + id + " twice");
		}

		List<String> names = new ArrayList<>(byId.size());
		for (Map.Entry<Integer, String> entry : byId.entrySet()) {
			if (entry.getKey() != names.size())
				throw new IllegalStateException(context + " has no packet with ID " + names.size());
			names.add(entry.getValue());
		}

		return names;
	}

	private static int id(String context, String key) {
		try {
			return key.startsWith("0x") ? Integer.parseInt(key.substring(2), 16) : Integer.parseInt(key);
		} catch (NumberFormatException exception) {
			throw new IllegalStateException(context + " maps the packet ID '" + key + "', which is no number", exception);
		}
	}
}
