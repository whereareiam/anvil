package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ChunkPackets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The chunk translation of the 1.16 chain on the MCProtocolLib 1.17.1 release, whose library sizes a column's
 * sections by the bits of the column's section mask, a multiple of 64, and whose levels may start below height 0.
 */
class ChunkPacketsTest {
	/**
	 * The sections the library reads for a level of 24 sections: as many as the mask's {@code BitSet} has bits.
	 */
	private static final int MASK_BITS = 64;

	private final ChunkPackets packets = new ChunkPackets(PacketDemand.EVERY);

	@Test
	void keepsTheSectionsOfALevelBelowHeightZeroInTheirOrder() {
		Chunk[] chunks = new Chunk[MASK_BITS];
		chunks[0] = airFirst();
		chunks[0].set(1, 2, 3, 9);
		chunks[23] = airFirst();
		chunks[23].set(15, 15, 15, 7);

		ClientboundLevelChunkWithLight chunk = assertInstanceOf(ClientboundLevelChunkWithLight.class, packets.decode(
				new ServerChunkDataPacket(new Column(-2, 5, chunks, new CompoundTag[0], new CompoundTag(""),
						new int[1536]))));

		assertEquals(new ChunkPosition(-2, 5), chunk.getPosition());
		assertEquals(MASK_BITS, chunk.getSections().size());
		assertEquals(9, chunk.getSections().getFirst().blockState(1, 2, 3));
		assertEquals(LevelChunkSection.EMPTY, chunk.getSections().get(1));
		assertEquals(7, chunk.getSections().get(23).blockState(15, 15, 15));
		assertEquals(LevelChunkSection.EMPTY, chunk.getSections().get(24));
	}

	private static Chunk airFirst() {
		Chunk chunk = new Chunk();
		chunk.set(0, 0, 0, LevelChunkSection.AIR);
		return chunk;
	}
}
