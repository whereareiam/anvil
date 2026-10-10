package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional overrides used when creating one simulated player in a running scenario.
 */
@Value
@Builder(toBuilder = true)
public class PlayerOptions {
	/**
	 * Name that identifies the player within its scenario, unique among its players. It is also the Minecraft
	 * username unless {@link #getUsername() username} or an account supplies another one.
	 */
	@NotNull String name;

	/**
	 * Minecraft username an offline player logs in with, when it differs from {@link #getName() name}. Several
	 * players may share one username, so a test can connect the same username twice. It is refused for an
	 * authentication mode that uses an account, whose account supplies the username.
	 *
	 * <pre>{@code
	 * PlayerOptions.builder().name("alice-again").username("Alice").build();
	 * }</pre>
	 */
	@Nullable String username;

	/**
	 * Optional labels for tooling; they never replace the player name or its username.
	 */
	@Nullable PresentationMetadata metadata;

	/**
	 * Native Minecraft version; when set it must equal the version of every server the player can reach.
	 */
	@Nullable String clientVersion;
	@Nullable String connectTo;

	/**
	 * Protocol library for this player, overriding the scenario and engine defaults.
	 */
	@Nullable String protocolLibrary;

	@NotNull
	@Builder.Default
	AuthenticationMode authentication = AuthenticationMode.OFFLINE;

	/** Local account identifier resolved by the selected protocol library. */
	@Nullable String accountId;

	/**
	 * Identity verified by a session server the scenario chooses. It replaces {@link #getAccountId() accountId}
	 * for an authentication mode that uses an account; declaring both is refused.
	 */
	@Nullable SessionIdentity sessionIdentity;
}
