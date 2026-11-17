package me.whereareiam.anvil.testkit.physics.harness.v26;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseRelative;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The packets a Paper server sends the vanilla client of Minecraft 26.1.2 to 26.3 that joins: the login into the overworld
 * as a fresh survival player, the position packets of its teleports, the game event that lets the client wait for the
 * level's chunks, and the chunks themselves.
 */
final class V26Server {
	/**
	 * The view and simulation distance of a Paper server's defaults.
	 */
	static final int VIEW_DISTANCE = 10;
	static final int MAX_PLAYERS = 20;

	private V26Server() {
	}

	static ClientboundLoginPacket login(RegistryAccess registries) {
		return V26Client.login(V26Levels.overworld(registries), VIEW_DISTANCE, MAX_PLAYERS);
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
