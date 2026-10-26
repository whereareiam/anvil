package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.MovementState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/**
 * Movement driven over a player's typed channel: every action is a request to the worker's movement extension, and
 * reads ask the worker for the client's current state. Tick waits poll that state through the channel's wait.
 */
final class ChannelMovement implements Movement {
	private static final Duration TICK = Duration.ofMillis(50);
	private static final Duration TICK_WAIT_MARGIN = Duration.ofSeconds(5);

	private final ProtocolChannel channel;

	ChannelMovement(@NotNull ProtocolChannel channel) {
		this.channel = channel;
	}

	@Override
	public void move(@NotNull Position position) {
		channel.request(MovementChannel.MOVE, position);
	}

	@Override
	public void physics(boolean enabled) {
		String unavailable = enabled ? state().getPhysicsUnavailable() : null;
		if (unavailable != null) throw new IllegalStateException("Client physics is unavailable: " + unavailable);

		channel.request(MovementChannel.PHYSICS, enabled);
	}

	@Override
	public boolean physics() {
		return state().isPhysics();
	}

	@Override
	public void input(@NotNull MovementInput input) {
		channel.request(MovementChannel.INPUT, input);
	}

	@Override
	public void look(float yaw, float pitch) {
		channel.request(MovementChannel.LOOK, new ViewRotation(yaw, pitch));
	}

	@Override
	public @NotNull Position position() {
		Position position = state().getPosition();
		if (position == null)
			throw new IllegalStateException("The client does not know where it is: the server has not placed it on its current connection");

		return position;
	}

	@Override
	public @NotNull Motion motion() {
		return state().getMotion();
	}

	@Override
	public void awaitTicks(int ticks) {
		awaitTicks(ticks, TICK.multipliedBy(2L * ticks).plus(TICK_WAIT_MARGIN));
	}

	@Override
	public void awaitTicks(int ticks, @NotNull Duration timeout) {
		if (ticks < 1) throw new IllegalArgumentException("ticks must be at least 1, not " + ticks);

		long target = state().getTicks() + ticks;
		channel.await(() -> state().getTicks() >= target, "run " + ticks + " physics ticks", timeout);
	}

	private @NotNull MovementState state() {
		return Objects.requireNonNull(channel.request(MovementChannel.STATE, null));
	}
}
