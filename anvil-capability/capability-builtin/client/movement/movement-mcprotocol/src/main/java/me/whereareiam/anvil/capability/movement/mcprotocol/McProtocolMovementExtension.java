package me.whereareiam.anvil.capability.movement.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.movement.MovementBinding;
import me.whereareiam.anvil.capability.movement.MovementProvider;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs movement into MCProtocolLib workers through the segment selected for the worker's release: each player's
 * binding takes the {@link MovementPackets} adapter from the worker, which selected the one movement segment for
 * the loaded release and verified its linkage.
 */
public final class McProtocolMovementExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return MovementProvider.ID;
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	/**
	 * Installs the movement operation for one player.
	 *
	 * @throws AdapterUnavailableException when no movement segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		MovementPackets<?> packets = player.adapter(MovementPackets.class);
		return new MovementBinding<>(packets).bind(player, operations);
	}
}
