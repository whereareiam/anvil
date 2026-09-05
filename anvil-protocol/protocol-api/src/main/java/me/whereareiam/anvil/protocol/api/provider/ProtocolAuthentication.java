package me.whereareiam.anvil.protocol.api.provider;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Optional interactive authentication workflow owned by one protocol provider.
 * Credentials remain inside the provider's private store and must never be returned to tooling,
 * added to process arguments or environment variables, or emitted through the output callback.
 */
public interface ProtocolAuthentication {
	/**
	 * Authenticates and stores a named profile using this provider's account workflow.
	 *
	 * @param profile owner-local profile name
	 * @param output user-facing prompts and status; never access or refresh tokens
	 */
	void login(@NotNull String profile, @NotNull Consumer<String> output);

	/**
	 * Removes the named profile from this provider's private authentication store.
	 *
	 * @param profile owner-local profile name
	 * @param output user-facing status; never credentials
	 */
	void logout(@NotNull String profile, @NotNull Consumer<String> output);
}
