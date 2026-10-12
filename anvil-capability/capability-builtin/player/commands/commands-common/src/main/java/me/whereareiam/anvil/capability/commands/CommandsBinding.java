package me.whereareiam.anvil.capability.commands;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.commands.packet.CommandsPackets;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

/**
 * Worker-side commands behavior over the packets of one library release: it emits each command tree a native
 * session receives while it is still the player's current one, and a reset whenever a new native session starts.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class CommandsBinding<S> {
	private final @NotNull CommandsPackets<S> packets;

	/**
	 * Observes every native session the player connects with. Commands register no operations; the host checks
	 * commands against the trees it receives.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that removes the native listener of the current session when the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		Subscription trees = player.bindNativeSession(nativeSession -> listen(player, nativeSession));
		return trees::close;
	}

	private Subscription listen(PlayerBindingContext<Object> player, Object nativeSession) {
		S session = packets.sessionType().cast(nativeSession);
		player.emit(CommandsOperations.RESET, null);
		Runnable detach = packets.listen(session, tree -> {
			if (player.isCurrentNativeSession(nativeSession)) player.emit(CommandsOperations.TREE, tree);
		});
		return detach::run;
	}
}
