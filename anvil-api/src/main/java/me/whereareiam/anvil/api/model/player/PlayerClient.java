package me.whereareiam.anvil.api.model.player;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.whereareiam.anvil.api.type.ClientLoader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The client a simulated player presents itself as: vanilla, or NeoForge with the payload channels of the mods it is
 * said to have. A NeoForge client claims exactly those channels in NeoForge's configuration and answers its steps
 * (registry sync, feature flags, extensible enums, data maps) as a NeoForge client does, so it joins a server whose
 * mods need their client counterparts. It runs no mod code: payloads on the mods' channels arrive and are left alone.
 *
 * <p>The channels of a NeoForge server Anvil runs come from its {@code Network} capability, which reads the channels
 * the server registered; for a server Anvil does not run, declare the channels its mods register.</p>
 *
 * <pre>{@code
 * Network network = anvil.processes().server("server").capability(Network.class);
 * SimulatedPlayer alice = anvil.players().create(PlayerOptions.builder()
 *         .name("Alice")
 *         .client(PlayerClient.neoforge(network.channels()))
 *         .build());
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerClient {
	private static final PlayerClient VANILLA = new PlayerClient(ClientLoader.VANILLA, List.of());

	/**
	 * The mod loader the client presents itself as.
	 */
	@NotNull ClientLoader loader;

	/**
	 * The payload channels the client claims; none for a vanilla client.
	 */
	@NotNull List<PayloadChannel> channels;

	/**
	 * Returns a vanilla client, which every player is unless it declares another.
	 *
	 * @return a vanilla client
	 */
	public static @NotNull PlayerClient vanilla() {
		return VANILLA;
	}

	/**
	 * Returns a NeoForge client that claims the given payload channels.
	 *
	 * @param channels the channels of the mods the client is said to have, such as a server's own from its
	 * {@code Network} capability
	 * @return a NeoForge client with those channels
	 */
	public static @NotNull PlayerClient neoforge(@NotNull List<PayloadChannel> channels) {
		return new PlayerClient(ClientLoader.NEOFORGE, List.copyOf(channels));
	}
}
