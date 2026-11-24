package me.whereareiam.anvil.capability.network;

import me.whereareiam.anvil.api.model.player.PayloadChannel;
import me.whereareiam.anvil.api.process.ProcessCapability;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The payload channels a NeoForge server registered with NeoForge's network: NeoForge's own and those of its mods,
 * each with its version, phase, direction and whether it is optional, as the server claims them to a client. A NeoForge
 * client that claims the same channels joins the server as a client with the same mods does; a test can also check
 * that a mod registered its channel.
 *
 * <pre>{@code
 * Network network = anvil.processes().server("server").capability(Network.class);
 * SimulatedPlayer alice = anvil.players().create(PlayerOptions.builder()
 *         .name("Alice")
 *         .client(PlayerClient.neoforge(network.channels()))
 *         .build());
 * }</pre>
 */
public interface Network extends ProcessCapability {
	/**
	 * Returns the payload channels the server registered, in no particular order.
	 *
	 * @return the server's channels
	 * @throws UnsupportedOperationException when the server's NeoForge release does not let its channels be read
	 */
	@NotNull List<PayloadChannel> channels();
}
