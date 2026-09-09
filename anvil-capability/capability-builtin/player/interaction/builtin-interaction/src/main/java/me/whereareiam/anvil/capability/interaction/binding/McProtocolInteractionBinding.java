package me.whereareiam.anvil.capability.interaction.binding;

import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.interaction.InteractionOperations;
import me.whereareiam.anvil.capability.interaction.internal.McProtocolInteractionAdapter;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.jetbrains.annotations.NotNull;

/**
 * Binds typed worker operations and native session access to the interaction implementation.
 */
public final class McProtocolInteractionBinding implements WorkerExtension<ClientSession> {
	public @NotNull String id() { return McProtocolInteractionProvider.ID; }
	public @NotNull String backendId() { return "mcprotocol"; }
	public @NotNull Class<ClientSession> backendType() { return ClientSession.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<ClientSession> player, @NotNull OperationRegistry operations) {
		var nativeActions = new McProtocolInteractionAdapter(player::backend,
				() -> player.viewRotation().getYaw(), () -> player.viewRotation().getPitch());
		operations.register(InteractionOperations.ITEM, request -> { nativeActions.useItem(request.getHand()); return null; });
		operations.register(InteractionOperations.BLOCK, request -> {
			nativeActions.block(BlockPosition.builder().x(request.getX()).y(request.getY()).z(request.getZ()).build(), request.getFace(), request.getHand());
			return null;
		});
		operations.register(InteractionOperations.ENTITY, request -> { nativeActions.entity(request.getEntityId(), request.getInteraction(), request.getHand()); return null; });
		return () -> { };
	}
}
