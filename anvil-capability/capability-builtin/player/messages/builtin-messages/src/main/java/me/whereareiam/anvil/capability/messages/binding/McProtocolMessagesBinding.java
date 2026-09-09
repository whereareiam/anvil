package me.whereareiam.anvil.capability.messages.binding;

import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.messages.MessagesOperations;
import me.whereareiam.anvil.capability.messages.internal.McProtocolMessagesAdapter;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.jetbrains.annotations.NotNull;

/**
 * Binds typed worker operations and native session access to the messages implementation.
 */
public final class McProtocolMessagesBinding implements WorkerExtension<ClientSession> {
	public @NotNull String id() { return McProtocolMessagesProvider.ID; }
	public @NotNull String backendId() { return "mcprotocol"; }
	public @NotNull Class<ClientSession> backendType() { return ClientSession.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<ClientSession> player, @NotNull OperationRegistry operations) {
		var nativeActions = new McProtocolMessagesAdapter(player::backend);
		operations.register(MessagesOperations.CHAT, request -> { nativeActions.chat(request.getText()); return null; });
		operations.register(MessagesOperations.COMMAND, request -> { nativeActions.command(request.getText()); return null; });
		var subscription = player.bindBackend(nativeSession -> {
			SessionAdapter listener = new SessionAdapter() {
				@Override
				public void packetReceived(@NotNull Session session, @NotNull Packet packet) {
					if (!player.isCurrentBackend(nativeSession) || !nativeSession.isConnected()) return;
					var observed = nativeActions.receive(packet);
					if (observed != null) player.emit(MessagesOperations.RECEIVED, new MessageText(observed));
				}
			};
			nativeSession.addListener(listener);
			return () -> nativeSession.removeListener(listener);
		});
		return subscription::close;
	}
}
