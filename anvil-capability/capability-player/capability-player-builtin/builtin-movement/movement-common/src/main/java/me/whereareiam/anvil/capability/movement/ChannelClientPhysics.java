package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client physics driven over a player's typed channel: the worker runs the physics, and each read asks it for the
 * client's movement now. A wait for ticks polls that movement through the channel's wait and stops as soon as physics
 * is off, with the reason the worker gives.
 */
final class ChannelClientPhysics implements ClientPhysics {
	private final ProtocolChannel channel;
	private final String playerName;

	ChannelClientPhysics(@NotNull ProtocolChannel channel, @NotNull String playerName) {
		this.channel = channel;
		this.playerName = playerName;
	}

	@Override
	public void enable() {
		String refusal = Objects.requireNonNull(channel.request(MovementChannel.PHYSICS, true)).getRefusal();
		if (refusal != null)
			throw new CapabilityUnavailableException("Client physics is unavailable for player '" + playerName + "': "
					+ refusal);
	}

	@Override
	public void disable() {
		channel.request(MovementChannel.PHYSICS, false);
	}

	@Override
	public boolean isEnabled() {
		return ChannelMovement.state(channel).isPhysics();
	}

	@Override
	public @NotNull Optional<String> failure() {
		return Optional.ofNullable(ChannelMovement.state(channel).getFailure());
	}

	@Override
	public void input(@NotNull MovementInput input) {
		channel.request(MovementChannel.INPUT, input);
	}

	@Override
	public @NotNull Motion motion() {
		return ChannelMovement.state(channel).getMotion();
	}

	@Override
	public void awaitTicks(int ticks, @NotNull Duration timeout) {
		if (ticks < 1) throw new IllegalArgumentException("A wait needs at least 1 tick, not " + ticks);

		MovementState start = ChannelMovement.state(channel);
		requireOn(start);

		long target = start.getTicks() + ticks;
		AtomicReference<MovementState> last = new AtomicReference<>(start);
		channel.await(() -> {
			MovementState now = ChannelMovement.state(channel);
			last.set(now);
			return !now.isPhysics() || now.getTicks() >= target;
		}, "run " + ticks + " physics ticks", timeout);
		requireOn(last.get());
	}

	private void requireOn(MovementState state) {
		if (state.isPhysics()) return;

		String reason = state.getFailure() == null ? "it is off" : "it turned off: " + state.getFailure();
		throw new IllegalStateException("Player '" + playerName + "' runs no client physics ticks: " + reason);
	}
}
