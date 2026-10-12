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
 * correction of the player's movement prediction, whichever came last.
 *
 * <p>Packets arrive on the network thread while operations send from others, so every access holds the monitor.</p>
 */
final class PositionTracker {
	private long runtimeEntityId;
	private @Nullable Vector3f position;

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
			}
			case MovePlayerPacket move -> {
				if (move.getRuntimeEntityId() == runtimeEntityId) position = move.getPosition();
			}
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
			case PlayerAuthInputPacket input -> position = input.getPosition();
			case MovePlayerPacket move -> {
				if (move.getRuntimeEntityId() == runtimeEntityId) position = move.getPosition();
			}
			default -> { }
		}
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
}
