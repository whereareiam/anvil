package me.whereareiam.anvil.capability.movement;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

/**
 * Worker-side movement behavior over the packets of one library release: it registers the movement operation
 * for a player, records the view rotation shared with other native actions and sends the packet through the
 * player's current native session.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class MovementBinding<S> {
	private final @NotNull MovementPackets<S> packets;

	/**
	 * Installs the movement operation for one player. The binding keeps no native listeners, so the returned
	 * binding releases nothing.
	 *
	 * @param player native lifecycle, session access and view rotation of the player
	 * @param operations typed operation registration for the player
	 * @return binding owned until the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		operations.register(MovementOperations.MOVE, position -> {
			move(player, position);
			return null;
		});

		return () -> { };
	}

	private void move(PlayerBindingContext<Object> player, Position position) {
		S session = packets.sessionType().cast(player.nativeSession());
		player.viewRotation(new ViewRotation(position.getYaw(), position.getPitch()));
		packets.move(session, position);
	}
}
