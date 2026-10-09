package me.whereareiam.anvil.capability.messages.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.messages.MessagesBinding;
import me.whereareiam.anvil.capability.messages.MessagesProvider;
import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs messages into MCProtocolLib workers through the segment selected for the worker's release: each player's
 * binding takes the {@link MessagesPackets} adapter from the worker, which selected the one messages segment for
 * the loaded release and verified its linkage.
 */
public final class McProtocolMessagesExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return MessagesProvider.ID;
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
	 * Installs the chat and command operations and the received-message listener for one player.
	 *
	 * @throws AdapterUnavailableException when no messages segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		MessagesPackets<?> packets = player.adapter(MessagesPackets.class);
		return new MessagesBinding<>(packets).bind(player, operations);
	}
}
