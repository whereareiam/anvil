package me.whereareiam.anvil.capability.packets.java;

import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

/**
 * Installs the built-in packets capability for players that speak the Java Edition protocol: it opens and closes the
 * host's taps through {@link PacketTaps}, which listens to the player's frames only while a tap is open and sends each
 * frame once for all the taps that take it. A player whose library hands over no frames has no packets capability.
 */
public final class JavaPacketsExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return PacketsChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		if (!protocol.handsOverFrames())
			throw new ProtocolUnavailableException("the player's library does not hand over the packets it receives");

		PacketTaps taps = new PacketTaps(player, protocol);
		taps.register(operations);

		return taps::closeAll;
	}
}
