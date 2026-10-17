package me.whereareiam.anvil.capability.entities.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.entities.EntitiesBinding;
import me.whereareiam.anvil.capability.entities.EntitiesProvider;
import me.whereareiam.anvil.capability.entities.packet.EntitiesPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs entities into MCProtocolLib workers through the segment selected for the worker's release: each player's
 * binding takes the {@link EntitiesPackets} adapter from the worker, which selected the one entities segment for the
 * loaded release and verified its linkage.
 */
public final class McProtocolEntitiesExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return EntitiesProvider.ID;
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
	 * Installs the entity packet listener for one player.
	 *
	 * @throws AdapterUnavailableException when no entities segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		EntitiesPackets<?> packets = player.adapter(EntitiesPackets.class);
		return new EntitiesBinding<>(packets).bind(player, operations);
	}
}
