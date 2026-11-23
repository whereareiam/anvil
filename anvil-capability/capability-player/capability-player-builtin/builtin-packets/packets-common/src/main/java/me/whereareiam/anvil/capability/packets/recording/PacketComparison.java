package me.whereareiam.anvil.capability.packets.recording;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.capability.packets.model.PacketDifference;
import me.whereareiam.anvil.capability.packets.model.PacketMask;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.edition.java.protocol.json.JavaProtocolJson;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares two recorded packet streams as {@code Packets.compare} describes: frames the mask names are left out of
 * both, the rest are aligned by their state and name, and each aligned pair is compared by bytes or, for a packet class
 * the mask compares by value, by its value in the Java protocol's JSON form without the ignored fields. Entity IDs in
 * values pair up by first appearance: a pair whose bytes are equal must still name paired entities, and a pair whose
 * bytes differ but whose values name entities is compared by value, so entities numbered differently in the two
 * streams still compare equal.
 */
public final class PacketComparison {
	private final ObjectMapper json = JavaProtocolJson.configure(new ObjectMapper());

	/**
	 * Compares two recordings.
	 *
	 * @param expected the recording to compare with
	 * @param actual the recording compared
	 * @param mask what to leave out
	 * @return every difference, in the order of the streams
	 * @throws IllegalArgumentException when the recordings are of different protocols, or the mask names a field a
	 * packet class does not have
	 */
	public @NotNull List<PacketDifference> compare(
			@NotNull RecordedPackets expected,
			@NotNull RecordedPackets actual,
			@NotNull PacketMask mask
	) {
		int protocol = expected.header().protocol();
		if (actual.header().protocol() != protocol)
			throw new IllegalArgumentException("A recording of protocol " + protocol + " cannot be compared with one of "
					+ "protocol " + actual.header().protocol() + ", whose packets mean other things");
		checkFields(mask);

		Stream left = Stream.of(expected.frames(), mask);
		Stream right = Stream.of(actual.frames(), mask);
		List<StreamAlignment.Step> steps = StreamAlignment.align(left.keys(), right.keys());
		if (steps == null) return List.of(diverging(left, right));

		EntityPairing entities = new EntityPairing();
		List<PacketDifference> differences = new ArrayList<>();
		for (StreamAlignment.Step step : steps) {
			if (step.actual() < 0) {
				differences.add(new PacketDifference(left.position(step.expected()), -1, "the actual stream lacks "
						+ left.keys().get(step.expected())));
				continue;
			}
			if (step.expected() < 0) {
				differences.add(new PacketDifference(-1, right.position(step.actual()), "the actual stream has an extra "
						+ right.keys().get(step.actual())));
				continue;
			}

			String difference = compare(left.frame(step.expected()), right.frame(step.actual()), mask, entities);
			if (difference != null)
				differences.add(new PacketDifference(left.position(step.expected()), right.position(step.actual()),
						left.keys().get(step.expected()) + ": " + difference));
		}

		return differences;
	}

	/**
	 * Compares one aligned pair.
	 *
	 * @return {@code null} when the pair is equal, else what differs
	 */
	private @Nullable String compare(ClientboundFrame expected, ClientboundFrame actual, PacketMask mask, EntityPairing entities) {
		ClientboundPacket expectedValue = expected.packet().orElse(null);
		ClientboundPacket actualValue = actual.packet().orElse(null);
		Set<String> ignored = expectedValue == null ? null : mask.fields().get(expectedValue.getClass());
		if (ignored != null) return values(expectedValue, actualValue, ignored, entities);

		byte[] expectedBytes = expected.payload();
		byte[] actualBytes = actual.payload();
		if (Arrays.equals(expectedBytes, actualBytes))
			return expectedValue == null ? null : values(expectedValue, actualValue, Set.of(), entities);
		if (expectedValue != null && actualValue != null && namesEntities(json.valueToTree(expectedValue)))
			return values(expectedValue, actualValue, Set.of(), entities);

		return bytes(expectedBytes, actualBytes);
	}

	private @Nullable String values(
			ClientboundPacket expected,
			@Nullable ClientboundPacket actual,
			Set<String> ignored,
			EntityPairing entities
	) {
		if (actual == null || actual.getClass() != expected.getClass())
			return "expected a " + expected.getClass().getSimpleName() + ", found "
					+ (actual == null ? "no value" : "a " + actual.getClass().getSimpleName());

		JsonNode left = json.valueToTree(expected);
		JsonNode right = json.valueToTree(actual);
		for (String field : ignored) {
			((ObjectNode) left).remove(field);
			((ObjectNode) right).remove(field);
		}

		return trees("", left, right, entities);
	}

	/**
	 * Compares two JSON trees, pairing the entity IDs they name.
	 *
	 * @return {@code null} when they are equal, else the path where they differ and how
	 */
	private static @Nullable String trees(String path, JsonNode expected, JsonNode actual, EntityPairing entities) {
		if (isEntity(expected) && isEntity(actual)) {
			String broken = entities.pair(expected.get(JavaProtocolJson.ENTITY).asInt(),
					actual.get(JavaProtocolJson.ENTITY).asInt());
			return broken == null ? null : at(path) + broken;
		}
		if (expected.isObject() && actual.isObject()) {
			Set<String> fields = new TreeSet<>();
			expected.fieldNames().forEachRemaining(fields::add);
			actual.fieldNames().forEachRemaining(fields::add);
			for (String field : fields) {
				JsonNode left = expected.get(field);
				JsonNode right = actual.get(field);
				if (left == null || right == null) return at(path + "." + field) + "only one value has it";

				String difference = trees(path + "." + field, left, right, entities);
				if (difference != null) return difference;
			}
			return null;
		}
		if (expected.isArray() && actual.isArray()) {
			if (expected.size() != actual.size())
				return at(path) + "expected " + expected.size() + " elements, found " + actual.size();
			for (int index = 0; index < expected.size(); index++) {
				String difference = trees(path + "[" + index + "]", expected.get(index), actual.get(index), entities);
				if (difference != null) return difference;
			}
			return null;
		}

		return expected.equals(actual) ? null : at(path) + "expected " + expected + ", found " + actual;
	}

	private static boolean namesEntities(JsonNode node) {
		if (isEntity(node)) return true;
		for (Iterator<JsonNode> children = node.elements(); children.hasNext(); )
			if (namesEntities(children.next())) return true;

		return false;
	}

	private static boolean isEntity(JsonNode node) {
		return node.isObject() && node.size() == 1 && node.has(JavaProtocolJson.ENTITY);
	}

	private static String at(String path) {
		return path.isEmpty() ? "" : path.substring(1) + ": ";
	}

	private static String bytes(byte[] expected, byte[] actual) {
		int common = Math.min(expected.length, actual.length);
		int offset = 0;
		while (offset < common && expected[offset] == actual[offset]) offset++;

		return "the bytes differ from offset " + offset + " (expected " + expected.length + " bytes, found "
				+ actual.length + ")";
	}

	private static PacketDifference diverging(Stream left, Stream right) {
		int index = 0;
		while (index < left.keys().size() && index < right.keys().size()
				&& left.keys().get(index).equals(right.keys().get(index))) index++;

		return new PacketDifference(index < left.keys().size() ? left.position(index) : -1,
				index < right.keys().size() ? right.position(index) : -1,
				"the streams differ in more than " + StreamAlignment.MOST_DIFFERENCES + " places from here on");
	}

	/**
	 * Checks that each field a mask names is a property of its packet class in the Java protocol's JSON form.
	 */
	private void checkFields(PacketMask mask) {
		for (Map.Entry<Class<? extends ClientboundPacket>, Set<String>> masked : mask.fields().entrySet()) {
			Set<String> properties = new TreeSet<>();
			for (BeanPropertyDefinition property : json.getSerializationConfig()
					.introspect(json.constructType(masked.getKey())).findProperties())
				properties.add(property.getName());
			for (String field : masked.getValue())
				if (!properties.contains(field))
					throw new IllegalArgumentException(masked.getKey().getSimpleName() + " has no field " + field
							+ "; its fields are " + properties);
		}
	}

	/**
	 * The frames of one stream that the mask keeps, with their keys and their positions in the whole stream.
	 */
	private record Stream(List<ClientboundFrame> frames, List<String> keys, List<Integer> positions) {
		static Stream of(List<ClientboundFrame> all, PacketMask mask) {
			List<ClientboundFrame> frames = new ArrayList<>();
			List<String> keys = new ArrayList<>();
			List<Integer> positions = new ArrayList<>();
			for (int position = 0; position < all.size(); position++) {
				ClientboundFrame frame = all.get(position);
				String name = frame.name().orElse(null);
				if (name != null && mask.names().contains(name)) continue;

				frames.add(frame);
				keys.add(frame.state() + " " + (name == null ? "#" + frame.id() : name));
				positions.add(position);
			}

			return new Stream(frames, keys, positions);
		}

		ClientboundFrame frame(int index) {
			return frames.get(index);
		}

		int position(int index) {
			return positions.get(index);
		}
	}
}
