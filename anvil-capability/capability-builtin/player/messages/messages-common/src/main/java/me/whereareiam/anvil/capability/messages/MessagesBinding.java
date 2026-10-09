package me.whereareiam.anvil.capability.messages;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

/**
 * Worker-side messages behavior over the packets of one library release: it registers the chat and command
 * operations for a player, sends them through the player's current native session and emits each message a
 * native session receives while it is still the player's current one.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class MessagesBinding<S> {
	private final @NotNull MessagesPackets<S> packets;

	/**
	 * Installs the messages operations for one player and observes every native session the player connects with.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that removes the native listener of the current session when the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		operations.register(MessagesOperations.CHAT, request -> {
			packets.chat(session(player), request.getText());
			return null;
		});
		operations.register(MessagesOperations.COMMAND, request -> {
			packets.command(session(player), request.getText());
			return null;
		});

		Subscription received = player.bindNativeSession(nativeSession -> listen(player, nativeSession));
		return received::close;
	}

	private Subscription listen(PlayerBindingContext<Object> player, Object nativeSession) {
		Runnable detach = packets.listen(packets.sessionType().cast(nativeSession), text -> {
			if (player.isCurrentNativeSession(nativeSession)) player.emit(MessagesOperations.RECEIVED, new MessageText(text));
		});
		return detach::run;
	}

	private S session(PlayerBindingContext<Object> player) {
		return packets.sessionType().cast(player.nativeSession());
	}
}
