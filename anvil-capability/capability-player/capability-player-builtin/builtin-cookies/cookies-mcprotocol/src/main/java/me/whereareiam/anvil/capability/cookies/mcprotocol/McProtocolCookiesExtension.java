package me.whereareiam.anvil.capability.cookies.mcprotocol;

import me.whereareiam.anvil.capability.cookies.CookiesBinding;
import me.whereareiam.anvil.capability.cookies.CookiesProvider;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPackets;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs cookies into MCProtocolLib workers through the segment selected for the worker's release: each player's
 * binding takes the {@link CookiesPackets} adapter from the worker, which selected the one cookies segment for the
 * loaded release and verified its linkage. Releases before Minecraft 1.20.5 have no cookies, so no segment serves
 * them and the worker reports the capability unavailable.
 */
public final class McProtocolCookiesExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return CookiesProvider.ID;
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
	 * Installs the cookies operations and the cookie packet listener for one player.
	 *
	 * @throws ProtocolUnavailableException when no cookies segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		CookiesPackets<?> packets = player.adapter(CookiesPackets.class);
		return new CookiesBinding<>(packets).bind(player, operations);
	}
}
