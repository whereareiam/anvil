package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.chunk.ChunkSection;
import com.github.steveice10.mc.protocol.data.game.chunk.DataPalette;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.PaletteType;
import com.github.steveice10.mc.protocol.data.game.chunk.palette.SingletonPalette;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.level.LightUpdateData;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockChangeEntry;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityInfo;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.packetlib.io.stream.StreamNetOutput;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSectionBlocksUpdate;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldPacketsTest {
	private static final int BIOME_BITS = 6;
	/**
	 * Bytes the game may leave after a column's sections, since it sizes a column's bytes by more than they take.
	 */
	private static final int PADDING = 2;

	private final ConnectionLevel level = new ConnectionLevel();
	private final WorldPackets packets = new WorldPackets(level);

	@Test
	void decodesAsManySectionsAsTheLevelHasFromTheirPalettes() throws IOException {
		ChunkSection paletted = airFirst();
		paletted.setBlock(1, 2, 3, 9);
		ChunkSection direct = airFirst();
		for (int state = 1; state <= 300; state++) direct.setBlock(state % 16, state / 256, (state / 16) % 16, state);

		level.enter(new Dimension("minecraft:overworld", -64, 48));
		ClientboundLevelChunkWithLight chunk = assertInstanceOf(ClientboundLevelChunkWithLight.class,
				packets.decode(chunk(-2, 5, paletted, uniform(7), direct)));

		assertEquals(new ChunkPosition(-2, 5), chunk.getPosition());
		assertEquals(3, chunk.getSections().size());
		LevelChunkSection first = chunk.getSections().getFirst();
		assertEquals(9, first.blockState(1, 2, 3));
		assertEquals(LevelChunkSection.AIR, first.blockState(0, 0, 0));
		assertEquals(LevelChunkSection.uniform(7), chunk.getSections().get(1));
		assertEquals(300, chunk.getSections().get(2).blockState(300 % 16, 1, (300 / 16) % 16));
		assertEquals(17, chunk.getSections().get(2).blockState(1, 0, 1));
	}

	@Test
	void refusesAColumnBeforeTheServerNamedTheLevel() {
		assertThrows(IllegalStateException.class, () -> packets.decode(chunk(0, 0, uniform(7))));
	}

	@Test
	void decodesForgettingAColumnAndBlockUpdates() {
		assertEquals(new ClientboundForgetLevelChunk(new ChunkPosition(3, -4)),
				packets.decode(new ClientboundForgetLevelChunkPacket(3, -4)));
		assertEquals(new ClientboundBlockUpdate(change(1, -60, 2, 5)),
				packets.decode(new ClientboundBlockUpdatePacket(new BlockChangeEntry(new Position(1, -60, 2), 5))));
		assertEquals(new ClientboundSectionBlocksUpdate(List.of(change(16, 0, 0, 1), change(17, 1, 0, 2))),
				packets.decode(new ClientboundSectionBlocksUpdatePacket(1, 0, 0, false,
						new BlockChangeEntry(new Position(16, 0, 0), 1), new BlockChangeEntry(new Position(17, 1, 0), 2))));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
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
	private static ChunkSection airFirst() {
		ChunkSection section = new ChunkSection();
		section.setBlock(0, 0, 0, LevelChunkSection.AIR);
		return section;
	}

	private static ChunkSection uniform(int state) {
		return new ChunkSection(4096, new DataPalette(new SingletonPalette(state), null, PaletteType.CHUNK,
				DataPalette.GLOBAL_PALETTE_BITS_PER_ENTRY), DataPalette.createForBiome(BIOME_BITS));
	}

	private static ClientboundLevelChunkWithLightPacket chunk(int x, int z, ChunkSection... sections) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		StreamNetOutput out = new StreamNetOutput(bytes);
		for (ChunkSection section : sections) ChunkSection.write(out, section, BIOME_BITS);
		out.writeBytes(new byte[PADDING]);

		LightUpdateData light = new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(),
				List.of(), true);
		return new ClientboundLevelChunkWithLightPacket(x, z, bytes.toByteArray(), new CompoundTag(""),
				new BlockEntityInfo[0], light);
	}

	private static BlockChange change(int x, int y, int z, int state) {
		return new BlockChange(BlockPosition.builder().x(x).y(y).z(z).build(), state);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
