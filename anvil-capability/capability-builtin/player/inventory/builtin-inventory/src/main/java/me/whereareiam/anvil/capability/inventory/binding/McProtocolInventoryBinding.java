package me.whereareiam.anvil.capability.inventory.binding;

import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.inventory.InventoryOperations;
import me.whereareiam.anvil.capability.inventory.internal.McProtocolInventoryAdapter;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.jetbrains.annotations.NotNull;

/**
 * Binds typed worker operations and native session access to the inventory implementation.
 */
public final class McProtocolInventoryBinding implements WorkerExtension<ClientSession> {
	public @NotNull String id() { return McProtocolInventoryProvider.ID; }
	public @NotNull String backendId() { return "mcprotocol"; }
	public @NotNull Class<ClientSession> backendType() { return ClientSession.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<ClientSession> player, @NotNull OperationRegistry operations) {
		var nativeActions = new McProtocolInventoryAdapter(player::backend);
		operations.register(InventoryOperations.SELECT, request -> { nativeActions.select(request.getSlot()); return null; });
		operations.register(InventoryOperations.CLICK, request -> { nativeActions.click(request.getSlot(), request.getClick(), request.getButton()); return null; });
		var subscription = player.bindBackend(nativeSession -> {
			SessionAdapter listener = new SessionAdapter() {
				@Override
				public void packetReceived(@NotNull Session session, @NotNull Packet packet) {
					if (!player.isCurrentBackend(nativeSession) || !nativeSession.isConnected()) return;
					var observed = nativeActions.receive(packet);
					if (observed != null) player.emit(InventoryOperations.CHANGED, observed);
				}
			};
			nativeSession.addListener(listener);
			return () -> nativeSession.removeListener(listener);
		});
		return subscription::close;
	}
}
