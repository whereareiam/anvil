package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional overrides used when creating one simulated player in a running scenario: who logs in, with which
 * client, and where to. How the player logs in is grouped in its {@link PlayerLogin}, and where it connects in its
 * {@link PlayerConnection}.
 *
 * <pre>{@code
 * SimulatedPlayer alice = players.create(PlayerOptions.builder()
 *         .name("alice-again")
 *         .login(PlayerLogin.offline("Alice"))
 *         .connection(PlayerConnection.builder().target("proxy").virtualHost("lobby.example.test").build())
 *         .build());
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class PlayerOptions {
	/**
	 * Name that identifies the player within its scenario, unique among its players. It is also the Minecraft
	 * username unless the {@link #getLogin() login} supplies another one.
	 */
	@NotNull String name;

	/**
	 * Authentication mode and identity the player logs in with; by default offline, as its name.
	 */
	@NotNull
	@Builder.Default
	PlayerLogin login = PlayerLogin.offline();

	/**
	 * Native Minecraft version; when set it must equal the version of every server the player can reach.
	 */
	@Nullable String clientVersion;

	/**
	 * Protocol library for this player, overriding the scenario and engine defaults.
	 */
	@Nullable String protocolLibrary;

	/**
	 * Process the player joins, the host it announces and the address it connects from; by default the
	 * scenario entrypoint at its real address.
	 */
	@NotNull
	@Builder.Default
	PlayerConnection connection = PlayerConnection.entrypoint();

	/**
	 * Optional labels for tooling; they never replace the player name or its username.
	 */
	@Nullable PresentationMetadata metadata;
}
