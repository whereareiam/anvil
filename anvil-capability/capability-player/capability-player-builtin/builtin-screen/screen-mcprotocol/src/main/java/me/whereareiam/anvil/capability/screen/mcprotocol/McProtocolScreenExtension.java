package me.whereareiam.anvil.capability.screen.mcprotocol;

import me.whereareiam.anvil.capability.screen.ScreenBinding;
import me.whereareiam.anvil.capability.screen.ScreenProvider;
import me.whereareiam.anvil.capability.screen.packet.ScreenPackets;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs the screen into MCProtocolLib workers through the segment selected for the worker's release: each
 * player's binding takes the {@link ScreenPackets} adapter from the worker, which selected the one screen segment
 * for the loaded release and verified its linkage.
 */
public final class McProtocolScreenExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return ScreenProvider.ID;
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
	 * Installs the title, action-bar and boss bar listener for one player.
	 *
	 * @throws ProtocolUnavailableException when no screen segment serves the worker's release or the segment does not
	 * link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		ScreenPackets<?> packets = player.adapter(ScreenPackets.class);
		return new ScreenBinding<>(packets).bind(player, operations);
	}
}
