package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockChangeRecord;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerBlockChangePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerMultiBlockChangePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUnloadChunkPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSectionBlocksUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldPacketsTest {
	private final WorldPackets packets = new WorldPackets();

	@Test
	void decodesAWholeColumnWithItsEmptySectionsAsAir() {
		Chunk[] chunks = new Chunk[16];
		chunks[0] = airFirst();
		chunks[0].set(1, 2, 3, 9);
		chunks[2] = airFirst();
		for (int state = 1; state <= 300; state++) chunks[2].set(state % 16, state / 256, (state / 16) % 16, state);

		ClientboundLevelChunkWithLight chunk = assertInstanceOf(ClientboundLevelChunkWithLight.class, packets.decode(
				new ServerChunkDataPacket(new Column(-2, 5, chunks, new CompoundTag[0], new CompoundTag(""),
						new int[1024]))));

		assertEquals(new ChunkPosition(-2, 5), chunk.getPosition());
		assertEquals(16, chunk.getSections().size());
		assertEquals(9, chunk.getSections().getFirst().blockState(1, 2, 3));
		assertEquals(LevelChunkSection.AIR, chunk.getSections().getFirst().blockState(1, 2, 4));
		assertEquals(LevelChunkSection.EMPTY, chunk.getSections().get(1));
		assertEquals(300, chunk.getSections().get(2).blockState(300 % 16, 1, (300 / 16) % 16));
	}

	@Test
	void decodesPartOfAColumnAsAnUpdateOfEveryBlockItCarries() {
		Chunk[] chunks = new Chunk[16];
		chunks[1] = airFirst();
		chunks[1].set(4, 5, 6, 9);

		ClientboundSectionBlocksUpdate update = assertInstanceOf(ClientboundSectionBlocksUpdate.class, packets.decode(
				new ServerChunkDataPacket(new Column(1, -1, chunks, new CompoundTag[0], new CompoundTag("")))));

		assertEquals(4096, update.getChanges().size());
		assertEquals(change(16, 16, -16, LevelChunkSection.AIR), update.getChanges().getFirst());
		assertEquals(List.of(change(20, 21, -10, 9)), update.getChanges().stream()
				.filter(change -> change.getBlockState() != LevelChunkSection.AIR).toList());
	}

	@Test
	void decodesForgettingAColumnAndBlockUpdates() {
		assertEquals(new ClientboundForgetLevelChunk(new ChunkPosition(3, -4)),
				packets.decode(new ServerUnloadChunkPacket(3, -4)));
		assertEquals(new ClientboundBlockUpdate(change(1, 60, 2, 5)),
				packets.decode(new ServerBlockChangePacket(new BlockChangeRecord(new Position(1, 60, 2), 5))));
		assertEquals(new ClientboundSectionBlocksUpdate(List.of(change(16, 0, 0, 1), change(17, 1, 0, 2))),
				packets.decode(new ServerMultiBlockChangePacket(1, 0, 0, false,
						new BlockChangeRecord(new Position(16, 0, 0), 1), new BlockChangeRecord(new Position(17, 1, 0), 2))));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheColumnsAndTheirUpdates() {
		assertEquals(Set.of(ClientboundBlockUpdate.class, ClientboundForgetLevelChunk.class,
				ClientboundLevelChunkWithLight.class, ClientboundSectionBlocksUpdate.class), packets.packets());
	}

	/**
	 * A section whose palette starts with air, as the game's sections do; the library's own empty section has no
	 * palette entry at all.
	 */
	private static Chunk airFirst() {
		Chunk chunk = new Chunk();
		chunk.set(0, 0, 0, LevelChunkSection.AIR);
		return chunk;
	}

	private static BlockChange change(int x, int y, int z, int state) {
		return new BlockChange(BlockPosition.builder().x(x).y(y).z(z).build(), state);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
