package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The position one connection's client believes its player has, at the eyes as Bedrock packets carry it: the game
 * start's position, then each position the client sends in a player move of its own entity or a player
 * authoritative input, and each position the server sets with a player move of the player's own entity or a
 * correction of the player's movement prediction, whichever came last. The rotation, pitch, yaw and head yaw, follows
 * the same packets except the corrections, which carry none.
 *
 * <p>Packets arrive on the network thread while operations send from others, so every access holds the monitor.</p>
 */
final class PositionTracker {
	private long runtimeEntityId;
	private @Nullable Vector3f position;
	private Vector3f rotation = Vector3f.ZERO;

	/**
	 * Applies a packet the server sent.
	 *
	 * @param packet received packet
	 */
	synchronized void received(@NotNull BedrockPacket packet) {
		switch (packet) {
			case StartGamePacket start -> {
				runtimeEntityId = start.getRuntimeEntityId();
				position = start.getPlayerPosition();
				// The game start rotation is the pitch followed by the yaw.
				rotation = Vector3f.from(start.getRotation().getX(), start.getRotation().getY(), start.getRotation().getY());
			}
			case MovePlayerPacket move -> moved(move);
			case CorrectPlayerMovePredictionPacket correction -> {
				if (correction.getPredictionType() != PredictionType.VEHICLE) position = correction.getPosition();
			}
			default -> { }
		}
	}

	/**
	 * Applies a packet the client is about to send.
	 *
	 * @param packet packet to send
	 */
	synchronized void sent(@NotNull BedrockPacket packet) {
		switch (packet) {
			case PlayerAuthInputPacket input -> {
				position = input.getPosition();
				rotation = input.getRotation();
			}
			case MovePlayerPacket move -> moved(move);
			default -> { }
		}
	}

	private void moved(MovePlayerPacket move) {
		if (move.getRuntimeEntityId() != runtimeEntityId) return;

		position = move.getPosition();
		rotation = move.getRotation();
	}

	/**
	 * Returns the player's position.
	 *
	 * @throws IllegalStateException before the game start arrived
	 */
	synchronized @NotNull ClientPosition position() {
		if (position == null) throw new IllegalStateException("The Bedrock session has not started its game yet");

		return ClientPosition.builder()
				.x(position.getX())
				.y(position.getY())
				.z(position.getZ())
				.build();
	}

	/**
	 * Returns the player's position at the eyes and its rotation, as a player authoritative input carries them.
	 *
	 * @throws IllegalStateException before the game start arrived
	 */
	synchronized @NotNull Pose pose() {
		if (position == null) throw new IllegalStateException("The Bedrock session has not started its game yet");

		return new Pose(position, rotation);
	}

	/**
	 * Where the player is and where it looks.
	 *
	 * @param eyes position at the eyes
	 * @param rotation pitch, yaw and head yaw in degrees
	 */
	record Pose(@NotNull Vector3f eyes, @NotNull Vector3f rotation) { }
}
