package me.whereareiam.anvil.capability.commands.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.commands.CommandsBinding;
import me.whereareiam.anvil.capability.commands.CommandsProvider;
import me.whereareiam.anvil.capability.commands.packet.CommandsPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs commands into MCProtocolLib workers through the segment selected for the worker's release: each player's
 * binding takes the {@link CommandsPackets} adapter from the worker, which selected the one commands segment for the
 * loaded release and verified its linkage.
 */
public final class McProtocolCommandsExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return CommandsProvider.ID;
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
	 * Installs the command-tree listener for one player.
	 *
	 * @throws AdapterUnavailableException when no commands segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		CommandsPackets<?> packets = player.adapter(CommandsPackets.class);
		return new CommandsBinding<>(packets).bind(player, operations);
	}
}
