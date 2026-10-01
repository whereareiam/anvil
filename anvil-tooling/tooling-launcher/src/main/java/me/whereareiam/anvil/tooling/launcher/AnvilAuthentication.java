package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.launcher.config.EngineDefaults;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import me.whereareiam.anvil.protocol.api.provider.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Signs a stored account in or out through the selected protocol provider.
 * <p>
 * Shared by the IntelliJ account manager and the Gradle {@code anvilAccount} task. Options:
 * <pre>{@code
 * --account-id <id>      account to store or remove (required)
 * --accounts-dir <path>  account directory; defaults to anvil.accountsDir, then ~/.anvil/accounts
 * --provider <id>        protocol provider; defaults to anvil.protocol, then the sole installed one
 * --logout               removes the account instead of signing in
 * }</pre>
 * Prompts and status go to standard output. Credentials stay in the provider's store and are never
 * printed or accepted as arguments.
 */
public final class AnvilAuthentication {
	/**
	 * Runs one interactive sign-in, or one sign-out with {@code --logout}.
	 *
	 * @param arguments options described on this class
	 * @throws IllegalArgumentException when a required option is missing
	 * @throws IllegalStateException when the provider does not support interactive authentication
	 */
	public static void main(@NotNull String[] arguments) {
		List<String> options = Arrays.asList(arguments);
		EngineOptions defaults = EngineDefaults.resolve(EngineProperties.fromSystemProperties());
		String accountId = required(arguments, "--account-id");
		String accounts = optional(arguments, "--accounts-dir");
		String providerId = optional(arguments, "--provider");

		ProtocolAuthentication authentication = ProtocolProviderRegistry.discover()
				.select(providerId == null ? defaults.getProtocolId() : providerId)
				.authentication(accounts == null ? defaults.getAccountsDirectory() : Path.of(accounts))
				.orElseThrow(() -> new IllegalStateException("The selected protocol provider does not support interactive authentication"));
		if (options.contains("--logout")) {
			authentication.logout(accountId, System.out::println);
			return;
		}

		authentication.login(accountId, System.out::println);
	}

	private static @NotNull String required(String[] arguments, String option) {
		String value = optional(arguments, option);
		if (value == null || value.isBlank()) throw new IllegalArgumentException(option + " is required");

		return value;
	}

	private static @Nullable String optional(String[] arguments, String option) {
		for (int index = 0; index < arguments.length - 1; index++)
			if (option.equals(arguments[index])) return arguments[index + 1];

		return null;
	}
}
