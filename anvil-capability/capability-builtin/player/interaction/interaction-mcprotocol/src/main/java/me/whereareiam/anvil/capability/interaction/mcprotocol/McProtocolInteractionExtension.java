package me.whereareiam.anvil.capability.interaction.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.interaction.InteractionBinding;
import me.whereareiam.anvil.capability.interaction.InteractionProvider;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs interaction into MCProtocolLib workers through the segment selected for the worker's release: each
 * player's binding takes the {@link InteractionPackets} adapter from the worker, which selected the one interaction
 * segment for the loaded release and verified its linkage.
 */
public final class McProtocolInteractionExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return InteractionProvider.ID;
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
	 * Installs the interaction operations for one player.
	 *
	 * @throws AdapterUnavailableException when no interaction segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		InteractionPackets<?> packets = player.adapter(InteractionPackets.class);
		return new InteractionBinding<>(packets).bind(player, operations);
	}
}
