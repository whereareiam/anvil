package me.whereareiam.anvil.capability.movement.binding;

import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.movement.MovementOperations;
import me.whereareiam.anvil.capability.movement.internal.McProtocolMovementAdapter;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.jetbrains.annotations.NotNull;

/**
 * Binds typed worker operations and native session access to the movement implementation.
 */
public final class McProtocolMovementBinding implements WorkerExtension<ClientSession> {
	public @NotNull String id() { return McProtocolMovementProvider.ID; }
	public @NotNull String backendId() { return "mcprotocol"; }
	public @NotNull Class<ClientSession> backendType() { return ClientSession.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<ClientSession> player, @NotNull OperationRegistry operations) {
		var nativeActions = new McProtocolMovementAdapter(player::backend,
				(yaw, pitch) -> player.viewRotation(new ViewRotation(yaw, pitch)));
		operations.register(MovementOperations.MOVE, position -> { nativeActions.move(position); return null; });
		return () -> { };
	}
}
