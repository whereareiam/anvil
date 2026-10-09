package me.whereareiam.anvil.protocol.api.provider;

import org.jetbrains.annotations.NotNull;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Optional interactive authentication workflow owned by one protocol provider.
 * Credentials remain inside the provider's private store and must never be returned to tooling,
 * added to process arguments or environment variables, or emitted through the output callback.
 */
public interface ProtocolAuthentication {
	/**
	 * Lists locally stored accounts owned by this provider.
	 *
	 * @return account metadata without credential material
	 */
	default @NotNull Collection<AuthenticationAccount> accounts() { return List.of(); }

	/**
	 * Authenticates and stores a named account using this provider's account workflow.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing prompts and status; never access or refresh tokens
	 */
	void login(@NotNull String accountId, @NotNull Consumer<String> output);

	/**
	 * Removes the named account from this provider's private authentication store.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing status; never credentials
	 */
	void logout(@NotNull String accountId, @NotNull Consumer<String> output);
}
