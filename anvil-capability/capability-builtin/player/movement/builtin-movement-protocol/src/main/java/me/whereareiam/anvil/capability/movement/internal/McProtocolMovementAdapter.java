package me.whereareiam.anvil.capability.movement.internal;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Sends native movement packets and updates the view shared with other native actions.
 */
@RequiredArgsConstructor
public final class McProtocolMovementAdapter implements Movement {
	private final @NotNull Supplier<ClientSession> sessions;
	private final @NotNull BiConsumer<Float, Float> view;

	@Override
	public void move(@NotNull Position position) {
		view.accept(position.getYaw(), position.getPitch());
		sessions.get().send(new ServerboundMovePlayerPosRotPacket(position.isOnGround(), false,
				position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch()));
	}
}
