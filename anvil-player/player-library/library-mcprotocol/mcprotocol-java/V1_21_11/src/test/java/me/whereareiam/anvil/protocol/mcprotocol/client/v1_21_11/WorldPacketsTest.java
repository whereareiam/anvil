package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
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
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldPacketsTest {
	/**
	 * The registry sizes the library's section writer grows a section by; reading needs neither.
	 */
	private static final int BLOCK_STATES = 30_000;
	private static final int BIOMES = 64;

	private final ConnectionLevel level = new ConnectionLevel();
	private final WorldPackets packets = new WorldPackets(level);

	@Test
	void decodesEverySectionOfAColumnByTheBitsEachContainerAnnounces() {
		ChunkSection paletted = section();
		paletted.setBlock(1, 2, 3, 9);
		ChunkSection hashed = section();
		for (int state = 1; state <= 40; state++) hashed.setBlock(state % 16, 0, state / 16, state);
		ChunkSection direct = section();
		for (int state = 1; state <= 300; state++) direct.setBlock(state % 16, state / 256, (state / 16) % 16, state);

		level.enter(new Dimension("minecraft:overworld", -64, 64));
		ClientboundLevelChunkWithLight chunk = assertInstanceOf(ClientboundLevelChunkWithLight.class,
				packets.decode(chunk(-2, 5, paletted, section(), hashed, direct)));

		assertEquals(new ChunkPosition(-2, 5), chunk.getPosition());
		assertEquals(4, chunk.getSections().size());
		assertEquals(9, chunk.getSections().getFirst().blockState(1, 2, 3));
		assertEquals(LevelChunkSection.AIR, chunk.getSections().getFirst().blockState(0, 0, 0));
		assertEquals(LevelChunkSection.EMPTY, chunk.getSections().get(1));
		assertEquals(40, chunk.getSections().get(2).blockState(40 % 16, 0, 40 / 16));
		assertEquals(300, chunk.getSections().get(3).blockState(300 % 16, 1, (300 / 16) % 16));
	}

	@Test
	void decodesForgettingAColumnAndBlockUpdates() {
		assertEquals(new ClientboundForgetLevelChunk(new ChunkPosition(3, -4)),
				packets.decode(new ClientboundForgetLevelChunkPacket(3, -4)));
		assertEquals(new ClientboundBlockUpdate(change(1, -60, 2, 5)),
				packets.decode(new ClientboundBlockUpdatePacket(new BlockChangeEntry(Vector3i.from(1, -60, 2), 5))));
		assertEquals(new ClientboundSectionBlocksUpdate(List.of(change(16, 0, 0, 1))),
				packets.decode(new ClientboundSectionBlocksUpdatePacket(1, 0, 0,
						new BlockChangeEntry(Vector3i.from(16, 0, 0), 1))));
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
	 * An empty section, which the library starts with one palette entry of air, as the game does.
	 */
	private static ChunkSection section() {
		return new ChunkSection(0, DataPalette.createForBlockState(LevelChunkSection.AIR, BLOCK_STATES),
				DataPalette.createForBiome(0, BIOMES));
	}

	private static ClientboundLevelChunkWithLightPacket chunk(int x, int z, ChunkSection... sections) {
		ByteBuf buf = Unpooled.buffer();
		for (ChunkSection section : sections) MinecraftTypes.writeChunkSection(buf, section);

		LightUpdateData light = new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(),
				List.of());
		return new ClientboundLevelChunkWithLightPacket(x, z, ByteBufUtil.getBytes(buf), Map.of(),
				new BlockEntityInfo[0], light);
	}

	private static BlockChange change(int x, int y, int z, int state) {
		return new BlockChange(BlockPosition.builder().x(x).y(y).z(z).build(), state);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
