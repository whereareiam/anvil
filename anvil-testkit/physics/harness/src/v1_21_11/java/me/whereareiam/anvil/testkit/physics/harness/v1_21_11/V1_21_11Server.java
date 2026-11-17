package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseRelative;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The packets a Paper server sends the vanilla client of Minecraft 1.21.11 that joins: the login into the overworld
 * as a fresh survival player, the position packets of its teleports, the game event that lets the client wait for the
 * level's chunks, and the chunks themselves.
 */
final class V1_21_11Server {
	/**
	 * The view and simulation distance of a Paper server's defaults.
	 */
	private static final int VIEW_DISTANCE = 10;
	private static final int MAX_PLAYERS = 20;

	private V1_21_11Server() {
	}

	static ClientboundLoginPacket login(RegistryAccess registries) {
		CommonPlayerSpawnInfo spawn = new CommonPlayerSpawnInfo(V1_21_11Levels.overworld(registries), Level.OVERWORLD, 0L, GameType.SURVIVAL,
			null, false, false, Optional.empty(), 0, V1_21_11Levels.SEA_LEVEL);
		return new ClientboundLoginPacket(VanillaHarness.ENTITY_ID, false, Set.of(Level.OVERWORLD), MAX_PLAYERS, VIEW_DISTANCE, VIEW_DISTANCE,
			false, true, false, spawn, false);
	}

	static ClientboundPlayerPositionPacket position(Course.Teleport teleport) {
		PositionMoveRotation change = new PositionMoveRotation(new Vec3(teleport.getX(), teleport.getY(), teleport.getZ()),
			new Vec3(teleport.getDx(), teleport.getDy(), teleport.getDz()), teleport.getYaw(), teleport.getPitch());
		return new ClientboundPlayerPositionPacket(teleport.getId(), change, relatives(teleport.getRelatives()));
	}

	static ClientboundGameEventPacket levelChunksLoadStart() {
		return new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F);
	}

	/**
	 * The chunk packets of every loaded column of {@code level} within {@code radius} chunks of the origin.
	 */
	static List<ClientboundLevelChunkWithLightPacket> chunks(ClientLevel level, int radius) {
		List<ClientboundLevelChunkWithLightPacket> packets = new ArrayList<>();
		for (int x = -radius; x <= radius; x++)
			for (int z = -radius; z <= radius; z++)
				packets.add(new ClientboundLevelChunkWithLightPacket(level.getChunk(x, z), level.getLightEngine(), null, null));
		return packets;
	}

	private static Set<Relative> relatives(Set<CourseRelative> relatives) {
		Set<Relative> converted = EnumSet.noneOf(Relative.class);
		for (CourseRelative relative : relatives)
			converted.add(switch (relative) {
				case X -> Relative.X;
				case Y -> Relative.Y;
				case Z -> Relative.Z;
				case YAW -> Relative.Y_ROT;
				case PITCH -> Relative.X_ROT;
				case DELTA_X -> Relative.DELTA_X;
				case DELTA_Y -> Relative.DELTA_Y;
				case DELTA_Z -> Relative.DELTA_Z;
				case ROTATE_DELTA -> Relative.ROTATE_DELTA;
			});
		return converted;
	}
}
