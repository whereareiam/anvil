package me.whereareiam.anvil.capability.sounds.mcprotocol;

import me.whereareiam.anvil.capability.sounds.SoundsBinding;
import me.whereareiam.anvil.capability.sounds.SoundsProvider;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
import me.whereareiam.anvil.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs sounds into MCProtocolLib workers through the segment selected for the worker's release: each player's
 * binding takes the {@link SoundsPackets} adapter from the worker, which selected the one sounds segment for the
 * loaded release and verified its linkage.
 */
public final class McProtocolSoundsExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return SoundsProvider.ID;
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
	 * Installs the heard-sound listener for one player.
	 *
	 * @throws AdapterUnavailableException when no sounds segment serves the worker's release or the segment does not
	 * link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		SoundsPackets<?> packets = player.adapter(SoundsPackets.class);
		return new SoundsBinding<>(packets).bind(player, operations);
	}
}
