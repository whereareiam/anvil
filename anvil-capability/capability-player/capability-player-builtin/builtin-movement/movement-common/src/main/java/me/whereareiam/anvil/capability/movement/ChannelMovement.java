package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.player.api.channel.ProtocolChannel;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Movement driven over a player's typed channel: every action is a request to the worker's movement extension, and
 * each read asks the worker for the client's movement now. Its physics is a {@link ChannelClientPhysics} over the
 * same channel.
 */
final class ChannelMovement implements Movement {
	private final ProtocolChannel channel;
	private final ChannelClientPhysics physics;

	ChannelMovement(@NotNull ProtocolChannel channel, @NotNull String playerName) {
		this.channel = channel;
		this.physics = new ChannelClientPhysics(channel, playerName);
	}

	@Override
	public void move(@NotNull Position position) {
		channel.request(MovementChannel.MOVE, position);
	}

	@Override
	public void look(float yaw, float pitch) {
		channel.request(MovementChannel.LOOK, new ViewRotation(yaw, pitch));
	}

	@Override
	public @NotNull Optional<Position> position() {
		return Optional.ofNullable(state(channel).position());
	}

	@Override
	public @NotNull ClientPhysics physics() {
		return physics;
	}

	/**
	 * Reads the client's movement from the worker.
	 */
	static @NotNull MovementState state(@NotNull ProtocolChannel channel) {
		return Objects.requireNonNull(channel.request(MovementChannel.STATE, null));
	}
}
