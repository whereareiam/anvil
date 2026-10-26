package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WorldPacketsTest {
	/**
	 * The registry sizes the library's section writer grows a section by; reading needs neither.
	 */
	private static final int BLOCK_STATES = 30_000;
	private static final int BIOMES = 64;

	private final ConnectionLevel level = new ConnectionLevel();
	private final WorldPackets packets = new WorldPackets(level);

	@Test
	void skipsTheFluidCountEachSectionStartsWith() {
		ChunkSection water = section(3);
		water.setBlock(1, 2, 3, 86);
		ChunkSection stone = section(0);
		stone.setBlock(4, 5, 6, 1);
		level.enter(new Dimension("minecraft:overworld", -64, 32));

		ClientboundLevelChunkWithLight chunk = assertInstanceOf(ClientboundLevelChunkWithLight.class,
				packets.decode(chunk(water, stone)));

		assertEquals(new ChunkPosition(0, 0), chunk.getPosition());
		assertEquals(86, chunk.getSections().getFirst().blockState(1, 2, 3));
		assertEquals(LevelChunkSection.AIR, chunk.getSections().getFirst().blockState(0, 0, 0));
		assertEquals(1, chunk.getSections().get(1).blockState(4, 5, 6));
	}

	private static ChunkSection section(int fluids) {
		return new ChunkSection(0, fluids, DataPalette.createForBlockState(LevelChunkSection.AIR, BLOCK_STATES),
				DataPalette.createForBiome(0, BIOMES));
	}

	private static ClientboundLevelChunkWithLightPacket chunk(ChunkSection... sections) {
		ByteBuf buf = Unpooled.buffer();
		for (ChunkSection section : sections) MinecraftTypes.writeChunkSection(buf, section);

		LightUpdateData light = new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(),
				List.of());
		return new ClientboundLevelChunkWithLightPacket(0, 0, ByteBufUtil.getBytes(buf), Map.of(),
				new BlockEntityInfo[0], light);
	}
}
