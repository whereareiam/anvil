package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.launcher.config.EngineDefaults;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Signs a stored account in or out through a protocol library.
 * <p>
 * Shared by the IntelliJ account manager and the Gradle {@code anvilAccount} task. Options:
 * <pre>{@code
 * --account-id <id>      account to store or remove (required)
 * --accounts-dir <path>  account directory; defaults to anvil.accountsDir, then ~/.anvil/accounts
 * --library <id>         protocol library; defaults to anvil.protocolLibrary, then the sole installed
 *                        library offering authentication
 * --logout               removes the account instead of signing in
 * }</pre>
 * Prompts and status go to standard output. Credentials stay in the library's store and are never
 * printed or accepted as arguments.
 */
public final class AnvilAuthentication {
	/**
	 * Runs one interactive sign-in, or one sign-out with {@code --logout}.
	 *
	 * @param arguments options described on this class
	 * @throws IllegalArgumentException when a required option is missing or the library is not installed
	 * @throws IllegalStateException when no single library offering authentication can be selected
	 */
	public static void main(@NotNull String[] arguments) {
		List<String> options = Arrays.asList(arguments);
		EngineOptions defaults = EngineDefaults.resolve(EngineProperties.fromSystemProperties());
		String accountId = required(arguments, "--account-id");
		String accounts = optional(arguments, "--accounts-dir");
		String library = optional(arguments, "--library");
		Path directory = accounts == null ? defaults.getAccountsDirectory() : Path.of(accounts);

		ProtocolAuthentication authentication = authentication(
				ProtocolLibraryRegistry.discover(),
				library == null ? defaults.getProtocolLibrary() : library,
				directory
		);
		if (options.contains("--logout")) {
			authentication.logout(accountId, System.out::println);
			return;
		}

		authentication.login(accountId, System.out::println);
	}

	/**
	 * Selects the authentication workflow of the named library, or of the sole installed library
	 * offering authentication when no library is named.
	 *
	 * @param libraries installed protocol libraries
	 * @param library selected library identifier, or null for automatic selection
	 * @param accountsDirectory account store directory
	 * @return selected library's authentication workflow
	 * @throws IllegalArgumentException when the named library is not installed
	 * @throws IllegalStateException when the named library has no authentication, or automatic selection is empty or ambiguous
	 */
	static @NotNull ProtocolAuthentication authentication(
			@NotNull ProtocolLibraryRegistry libraries,
			@Nullable String library,
			@NotNull Path accountsDirectory
	) {
		if (library != null)
			return libraries.require(library).authentication(accountsDirectory)
					.orElseThrow(() -> new IllegalStateException("Protocol library '" + library
							+ "' does not support interactive authentication"));

		List<ProtocolAuthentication> candidates = new ArrayList<>();
		List<String> owners = new ArrayList<>();
		for (ProtocolLibraryProvider candidate : libraries.all()) {
			Optional<ProtocolAuthentication> offered = candidate.authentication(accountsDirectory);
			if (offered.isEmpty()) continue;

			candidates.add(offered.get());
			owners.add(candidate.id());
		}

		if (candidates.isEmpty())
			throw new IllegalStateException("No installed protocol library supports interactive authentication. Installed: "
					+ libraries.ids());
		if (candidates.size() > 1)
			throw new IllegalStateException("Protocol libraries " + owners + " support interactive authentication; "
					+ "select one with --library or anvil.protocolLibrary");

		return candidates.getFirst();
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
