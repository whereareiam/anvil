package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseRelative;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.RelativeMovement;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The packets a Paper server sends the vanilla clients of Minecraft 1.19.4 to 1.21.1 that join, besides the login,
 * which {@link V1_19Client} creates: the position packets of the player's teleports and the course's chunks.
 */
final class V1_19Server {
	/**
	 * The view and simulation distance of a Paper server's defaults.
	 */
	static final int VIEW_DISTANCE = 10;
	static final int MAX_PLAYERS = 20;

	private V1_19Server() {
	}

	static ClientboundPlayerPositionPacket position(Course.Teleport teleport) {
		return new ClientboundPlayerPositionPacket(teleport.getX(), teleport.getY(), teleport.getZ(), teleport.getYaw(), teleport.getPitch(),
			relatives(teleport.getRelatives()), teleport.getId());
	}

	/**
	 * The chunk packets of every loaded column of {@code level} within {@code radius} chunks of the origin.
	 */
	static List<ClientboundLevelChunkWithLightPacket> chunks(ClientLevel level, int radius) {
		List<ClientboundLevelChunkWithLightPacket> packets = new ArrayList<>();
		for (int x = -radius; x <= radius; x++)
			for (int z = -radius; z <= radius; z++)
				packets.add(V1_19Client.chunk(level.getChunk(x, z), level.getLightEngine()));
		return packets;
	}

	private static Set<RelativeMovement> relatives(Set<CourseRelative> relatives) {
		Set<RelativeMovement> converted = EnumSet.noneOf(RelativeMovement.class);
		for (CourseRelative relative : relatives)
			converted.add(relative(relative));
		return converted;
	}

	private static RelativeMovement relative(CourseRelative relative) {
		switch (relative) {
			case X:
				return RelativeMovement.X;
			case Y:
				return RelativeMovement.Y;
			case Z:
				return RelativeMovement.Z;
			case YAW:
				return RelativeMovement.Y_ROT;
			case PITCH:
				return RelativeMovement.X_ROT;
			default:
				throw new IllegalArgumentException("A position packet before Minecraft 1.21.2 carries no " + relative);
		}
	}
}
