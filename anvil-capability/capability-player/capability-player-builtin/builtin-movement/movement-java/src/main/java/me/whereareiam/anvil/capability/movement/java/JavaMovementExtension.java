package me.whereareiam.anvil.capability.movement.java;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.java.physics.ClientMovement;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

/**
 * Installs the built-in movement capability for players that speak the Java Edition protocol: each player's
 * {@link ClientMovement} moves and turns it as a test places it and, with client physics on, as the vanilla client of
 * its Minecraft version moves it.
 */
public final class JavaMovementExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return MovementChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations
	) {
		return ClientMovement.bind(player, operations);
	}
}
