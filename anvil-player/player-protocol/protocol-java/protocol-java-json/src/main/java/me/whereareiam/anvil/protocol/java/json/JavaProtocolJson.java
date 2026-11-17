package me.whereareiam.anvil.protocol.java.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.api.model.text.click.TextClick;
import me.whereareiam.anvil.protocol.api.model.text.content.TextContent;
import me.whereareiam.anvil.protocol.api.model.text.hover.TextHover;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventOperation;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPackets;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The JSON form of the Java Edition protocol's values, as everything that writes them down shares it: the worker
 * channel that carries a player's values between its worker and the host, and the files packet recordings are kept in.
 *
 * <p>Values are immutable classes, read back through their creator parameters. A value of a polymorphic type names its
 * kind in a {@code kind} member: a {@link ClientboundPacket} by its kind in {@link ClientboundPackets}, and a member of
 * a sealed type, such as an {@link NbtTag} or a text's content, by its class's simple name. An {@link EntityId} is an
 * object with the one member {@code entity}, so whatever reads the JSON can tell entity IDs from other numbers. A chunk
 * section is its encoding as the server sent it: {@code palette}, absent for a direct section, {@code bitsPerEntry} and
 * the packed {@code data}.</p>
 *
 * <pre>{@code
 * ObjectMapper mapper = JavaProtocolJson.configure(new ObjectMapper());
 * JsonNode chat = mapper.valueToTree(new ClientboundSystemChat(Text.literal("welcome"), false));
 * ClientboundPacket packet = mapper.treeToValue(chat, ClientboundPacket.class);
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JavaProtocolJson {
	/**
	 * The member that names a polymorphic value's kind.
	 */
	public static final String KIND = "kind";
	/**
	 * The one member of an entity ID's object.
	 */
	public static final String ENTITY = "entity";

	private static final String PALETTE = "palette";
	private static final String BITS = "bitsPerEntry";
	private static final String DATA = "data";

	private static final List<Class<?>> SEALED = List.of(
			TextContent.class,
			TextClick.class,
			TextHover.class,
			NbtTag.class,
			EntityDataValue.class,
			BossEventOperation.class
	);

	/**
	 * Teaches a mapper the JSON form of the protocol's values.
	 *
	 * @param mapper the mapper, which this changes
	 * @return the same mapper
	 */
	public static @NotNull ObjectMapper configure(@NotNull ObjectMapper mapper) {
		mapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
		for (Class<?> type : SEALED)
			sealed(mapper, type);

		mapper.addMixIn(ClientboundPacket.class, Kind.class);
		for (Map.Entry<String, Class<? extends ClientboundPacket>> kind : ClientboundPackets.kinds().entrySet())
			mapper.registerSubtypes(new NamedType(kind.getValue(), kind.getKey()));

		mapper.addMixIn(EntityId.class, EntityIdForm.class);
		mapper.registerModule(new SimpleModule("anvil-java-protocol")
				.addSerializer(LevelChunkSection.class, new SectionWriter())
				.addDeserializer(LevelChunkSection.class, new SectionReader()));
		return mapper;
	}

	/**
	 * Names each kind of a sealed type, and of the sealed types among its kinds, by its simple name.
	 */
	private static void sealed(ObjectMapper mapper, Class<?> type) {
		mapper.addMixIn(type, Kind.class);
		for (Class<?> kind : type.getPermittedSubclasses()) {
			if (kind.isSealed()) {
				sealed(mapper, kind);
				continue;
			}
			mapper.registerSubtypes(new NamedType(kind, kind.getSimpleName()));
		}
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = KIND)
	private interface Kind {
	}

	/**
	 * Writes an entity ID as {@code {"entity": 42}}.
	 */
	private abstract static class EntityIdForm {
		@JsonCreator
		EntityIdForm(@JsonProperty(ENTITY) int value) {
		}

		@JsonProperty(ENTITY)
		abstract int getValue();
	}

	/**
	 * Writes a chunk section's encoding.
	 */
	private static final class SectionWriter extends StdSerializer<LevelChunkSection> {
		private SectionWriter() {
			super(LevelChunkSection.class);
		}

		@Override
		public void serialize(LevelChunkSection section, JsonGenerator json, SerializerProvider provider)
				throws IOException {
			json.writeStartObject();
			int[] palette = section.palette();
			if (palette != null) {
				json.writeFieldName(PALETTE);
				json.writeArray(palette, 0, palette.length);
			}
			json.writeNumberField(BITS, section.bitsPerEntry());
			long[] data = section.data();
			json.writeFieldName(DATA);
			json.writeArray(data, 0, data.length);
			json.writeEndObject();
		}
	}

	/**
	 * Reads a chunk section's encoding through the factory of its kind, which checks it.
	 */
	private static final class SectionReader extends StdDeserializer<LevelChunkSection> {
		private SectionReader() {
			super(LevelChunkSection.class);
		}

		@Override
		public LevelChunkSection deserialize(JsonParser json, DeserializationContext context) throws IOException {
			JsonNode node = json.readValueAsTree();
			int bits = node.path(BITS).asInt();
			long[] data = context.readTreeAsValue(node.path(DATA), long[].class);
			JsonNode palette = node.get(PALETTE);
			try {
				if (palette == null) return LevelChunkSection.direct(bits, data);

				int[] entries = context.readTreeAsValue(palette, int[].class);
				return bits == 0 ? LevelChunkSection.uniform(entries[0]) : LevelChunkSection.paletted(entries, bits, data);
			} catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
				return context.reportInputMismatch(LevelChunkSection.class, "Invalid chunk section: %s", invalid.getMessage());
			}
		}
	}
}
