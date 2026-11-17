package me.whereareiam.anvil.protocol.java.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.overlay.BossEventUpdateProgress;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundBossEvent;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundLevelChunkWithLight;
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
	void refusesASectionWhoseEncodingBreaksItsRules() {
		String json = "{\"kind\":\"ClientboundLevelChunkWithLight\",\"position\":{\"x\":0,\"z\":0},\"sections\":"
				+ "[{\"palette\":[0],\"bitsPerEntry\":4,\"data\":[]}]}";

		assertThrows(Exception.class, () -> mapper.readValue(json, ClientboundPacket.class));
	}
}
