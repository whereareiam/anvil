package me.whereareiam.anvil.capability.dialogs.mcprotocol;

import me.whereareiam.anvil.capability.dialogs.DialogsBinding;
import me.whereareiam.anvil.capability.dialogs.DialogsProvider;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs dialogs into MCProtocolLib workers through the segment selected for the worker's release: each player's
 * binding takes the {@link DialogsPackets} adapter from the worker, which selected the one dialogs segment for the
 * loaded release and verified its linkage. Releases before Minecraft 1.21.6 have no dialogs segment, so their
 * players do not get the capability.
 */
public final class McProtocolDialogsExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return DialogsProvider.ID;
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
	 * Installs the click operation and the dialog listener for one player.
	 *
	 * @throws ProtocolUnavailableException when no dialogs segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		DialogsPackets<?> packets = player.adapter(DialogsPackets.class);
		return new DialogsBinding<>(packets).bind(player, operations);
	}
}
