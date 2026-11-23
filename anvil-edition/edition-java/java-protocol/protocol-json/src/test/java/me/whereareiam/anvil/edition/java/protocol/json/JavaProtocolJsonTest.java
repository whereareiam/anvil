package me.whereareiam.anvil.edition.java.protocol.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import me.whereareiam.anvil.edition.java.protocol.model.entity.CustomName;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.SharedFlags;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtInt;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtIntArray;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtString;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateProgress;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.BlockChange;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundBossEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaProtocolJsonTest {
	private final ObjectMapper mapper = JavaProtocolJson.configure(new ObjectMapper());

	@Test
	void readsBackEveryKindOfValueAsTheSamePacket() throws Exception {
		long[] data = new long[256];
		data[0] = 1;
		List<ClientboundPacket> packets = List.of(
				new ClientboundSystemChat(Text.literal("welcome"), false),
				new ClientboundBlockUpdate(new BlockChange(BlockPosition.builder().x(1).y(64).z(-3).build(), 9)),
				new ClientboundSetEntityData(new EntityId(42), List.of(new CustomName("Rex"),
						SharedFlags.builder().onFire(true).build())),
				new ClientboundRemoveEntities(List.of(new EntityId(1), new EntityId(2))),
				new ClientboundShowDialog(null, new NbtCompound(Map.of("type", new NbtString("notice"),
						"width", new NbtInt(200))), false),
				new ClientboundBossEvent(new UUID(0, 7), new BossEventUpdateProgress(0.5F)),
				new ClientboundLevelChunkWithLight(new ChunkPosition(2, -1), List.of(LevelChunkSection.EMPTY,
						LevelChunkSection.paletted(new int[]{0, 1}, 4, data), LevelChunkSection.direct(15,
								new long[1024])))
		);

		for (ClientboundPacket packet : packets) {
			String json = mapper.writerFor(ClientboundPacket.class).writeValueAsString(packet);

			assertEquals(packet, mapper.readValue(json, ClientboundPacket.class), json);
		}
	}

	@Test
	void namesAPacketByItsKindAndAnEntityIdByItsOwnMember() {
		JsonNode removed = mapper.valueToTree(new ClientboundRemoveEntities(List.of(new EntityId(42))));

		assertEquals("{\"kind\":\"ClientboundRemoveEntities\",\"entityIds\":[{\"entity\":42}]}", removed.toString());
	}

	@Test
	void writesEachValueUnderTheNamesOfItsFields() throws Exception {
		ObjectWriter packets = mapper.writerFor(ClientboundPacket.class);

		assertEquals("{\"kind\":\"ClientboundSystemChat\",\"text\":{\"content\":{\"kind\":\"LiteralContent\","
				+ "\"text\":\"welcome\"},\"style\":{\"color\":null,\"shadowColor\":null,\"decorations\":{},"
				+ "\"font\":null,\"insertion\":null,\"click\":null,\"hover\":null},\"children\":[]},\"overlay\":true}",
				packets.writeValueAsString(new ClientboundSystemChat(Text.literal("welcome"), true)));
		assertEquals("{\"kind\":\"ClientboundSetEntityData\",\"entityId\":{\"entity\":42},\"values\":[{\"kind\":\"SharedFlags\","
				+ "\"onFire\":true,\"sneaking\":false,\"sprinting\":false,\"swimming\":false,\"invisible\":false,"
				+ "\"glowing\":false,\"gliding\":false}]}",
				packets.writeValueAsString(new ClientboundSetEntityData(new EntityId(42),
						List.of(SharedFlags.builder().onFire(true).build()))));
		assertEquals("{\"kind\":\"ClientboundShowDialog\",\"registryId\":null,\"dialog\":{\"kind\":\"NbtCompound\","
				+ "\"entries\":{\"ids\":{\"kind\":\"NbtIntArray\",\"values\":[1,2]}}},\"configuration\":false}",
				packets.writeValueAsString(new ClientboundShowDialog(null,
						new NbtCompound(Map.of("ids", new NbtIntArray(new int[]{1, 2}))), false)));
	}

	@Test
	void refusesASectionWhoseEncodingBreaksItsRules() {
		String json = "{\"kind\":\"ClientboundLevelChunkWithLight\",\"position\":{\"x\":0,\"z\":0},\"sections\":"
				+ "[{\"palette\":[0],\"bitsPerEntry\":4,\"data\":[]}]}";

		assertThrows(Exception.class, () -> mapper.readValue(json, ClientboundPacket.class));
	}
}
