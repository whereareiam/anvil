package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.engine.config.EngineDefaults;
import me.whereareiam.anvil.engine.config.EngineProperties;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Signs a Microsoft account in and stores it, or removes a stored account. The account is not tied to a protocol
 * library: after the sign-in, one installed library of each Minecraft edition the installed libraries speak checks
 * what that edition's players can do with the account, and the result is printed per edition, such as
 * {@code example: available as Alice (<uuid>)}, with the checking library's reason when the account is unavailable.
 * <p>
 * Shared by the IntelliJ account manager and the Gradle {@code anvilLogin} and {@code anvilLogout} tasks. Options:
 * <pre>{@code
 * --account-id <id>      account to store or remove (required)
 * --accounts-dir <path>  account directory; defaults to anvil.accountsDir, then ~/.anvil/accounts
 * --logout               removes the account instead of signing in
 * }</pre>
 * Prompts and status go to standard output. Credentials stay in the account file and are never
 * printed or accepted as arguments.
 */
public final class AnvilAuthentication {
	/**
	 * Runs one interactive sign-in, or one sign-out with {@code --logout}.
	 *
	 * @param arguments options described on this class
	 * @throws IllegalArgumentException when a required option is missing
	 * @throws IllegalStateException when no installed protocol library plays online
	 */
	public static void main(@NotNull String[] arguments) {
		List<String> options = Arrays.asList(arguments);
		EngineOptions defaults = EngineDefaults.resolve(EngineProperties.fromSystemProperties());
		String accountId = required(arguments, "--account-id");
		String accounts = optional(arguments, "--accounts-dir");
		Path directory = accounts == null ? defaults.getAccountsDirectory() : Path.of(accounts);

		ProtocolAuthentication authentication = authentication(ProtocolLibraryRegistry.discover(), directory);
		if (options.contains("--logout")) {
			authentication.logout(accountId, System.out::println);
			return;
		}

		authentication.login(accountId, System.out::println);
	}

	/**
	 * Returns the account workflow of the installed protocol libraries.
	 *
	 * @param libraries installed protocol libraries
	 * @param accountsDirectory account directory
	 * @return the account workflow
	 * @throws IllegalStateException when no installed library plays online
	 */
	static @NotNull ProtocolAuthentication authentication(@NotNull ProtocolLibraryRegistry libraries, @NotNull Path accountsDirectory) {
		return libraries.authentication(accountsDirectory)
				.orElseThrow(() -> new IllegalStateException("No installed protocol library signs in with Microsoft accounts. "
						+ "Installed: " + libraries.ids()));
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
