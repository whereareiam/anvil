package me.whereareiam.anvil.integration.intellij.account.persistence;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.model.account.AccountCatalog;
import me.whereareiam.anvil.integration.intellij.model.account.AvailableAccount;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ project service that applies account-directory configuration and merges account sources.
 * <p>
 * The account directory override is personal and path-specific, so it is stored in the workspace file
 * rather than in shared project configuration.
 */
@RequiredArgsConstructor
@State(name = "AnvilProjectAccounts", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ConfiguredAccountLibrary implements AccountLibrary, PersistentStateComponent<ConfiguredAccountLibrary.Options> {
	private final AccountDirectoryRepository accountRepository = new AccountDirectoryRepository();
	private final @NotNull Project project;

	private @NotNull String directory = "";
	private boolean includeGlobal = true;

	/**
	 * Returns the service registered for the given IntelliJ project.
	 */
	public static @NotNull ConfiguredAccountLibrary getInstance(@NotNull Project project) {
		return (ConfiguredAccountLibrary) project.getService(AccountLibrary.class);
	}

	@Override
	public @NotNull Path directory() {
		return directory.isBlank() ? defaultDirectory() : Path.of(directory);
	}

	@Override
	public @NotNull Path defaultDirectory() {
		// Credentials stay out of the project tree; the location hash keeps each project's accounts separate.
		return Path.of(System.getProperty("user.home"), ".anvil", "projects", project.getLocationHash(), "accounts");
	}

	@Override
	public void setDirectory(@NotNull Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		directory = normalized.equals(defaultDirectory()) ? "" : normalized.toString();
	}

	@Override
	public boolean includesGlobal() {
		return includeGlobal;
	}

	@Override
	public void setIncludesGlobal(boolean include) {
		includeGlobal = include;
	}

	@Override
	public @NotNull AccountCatalog catalog() {
		Path projectDirectory = directory();
		Path globalDirectory = globalDirectory(projectDirectory);
		List<AvailableAccount> available = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		readAccounts(projectDirectory, AccountSource.PROJECT, available, problems);
		if (globalDirectory != null) {
			readAccounts(globalDirectory, AccountSource.GLOBAL, available, problems);
		}

		available.sort(Comparator.comparing(account -> account.getAccount().getAccountId()));
		addDuplicateAccountProblems(available, problems);

		Map<String, List<String>> projectPools = readPools(projectDirectory, problems);
		if (globalDirectory != null) {
			Map<String, List<String>> globalPools = readPools(globalDirectory, problems);
			for (String name : projectPools.keySet())
				if (globalPools.containsKey(name)) {
					problems.add(
							"Pool '"
									+ name
									+ "' also exists globally. Rename the project pool or disable global"
									+ " accounts before running."
					);
				}
		}

		return AccountCatalog.builder()
				.accounts(List.copyOf(available))
				.pools(projectPools)
				.problems(List.copyOf(problems))
				.build();
	}

	private void readAccounts(
			@NotNull Path directory,
			@NotNull AccountSource source,
			@NotNull List<AvailableAccount> available,
			@NotNull List<String> problems
	) {
		AccountDirectoryRepository.Listing listing = accountRepository.readAccounts(directory, source);
		available.addAll(listing.accounts());
		problems.addAll(listing.problems());
	}

	private void addDuplicateAccountProblems(
			@NotNull List<AvailableAccount> available,
			@NotNull List<String> problems
	) {
		Set<String> ids = new HashSet<>();
		for (AvailableAccount account : available)
			if (!ids.add(account.getAccount().getAccountId())) {
				problems.add(
						"Duplicate account ID '"
								+ account.getAccount().getAccountId()
								+ "'. Remove the project duplicate or disable global accounts before running."
				);
			}
	}

	private @NotNull Map<String, List<String>> readPools(
			@NotNull Path directory, @NotNull List<String> problems) {
		try {
			return new AccountPoolRepository(directory).read();
		} catch (IOException failure) {
			problems.add("Cannot read account pools: " + failure.getMessage());
			return Map.of();
		}
	}

	@Override
	public @NotNull AuthenticationAccount inspect(@NotNull Path source) throws IOException {
		return accountRepository.readMetadata(source);
	}

	@Override
	public @NotNull AuthenticationAccount importAccount(@NotNull Path source) throws IOException {
		return accountRepository.importAccount(source, directory());
	}

	@Override
	public void exportAccount(
			@NotNull AvailableAccount account, @NotNull Path destination) throws IOException {
		accountRepository.exportAccount(account, destination);
	}

	@Override
	public void removeAccount(@NotNull AvailableAccount account) throws IOException {
		accountRepository.removeAccount(account);
	}

	@Override
	public boolean isValidAccountId(@NotNull String id) {
		return accountRepository.isSafeAccountId(id);
	}

	@Override
	public void savePool(
			@Nullable String previous,
			@NotNull String name,
			@NotNull List<String> accountIds
	) throws IOException {
		new AccountPoolRepository(directory()).save(previous, name, accountIds);
	}

	@Override
	public void removePool(@NotNull String name) throws IOException {
		new AccountPoolRepository(directory()).remove(name);
	}

	private @Nullable Path globalDirectory(@NotNull Path projectDirectory) {
		if (!includeGlobal) return null;

		Path global = ApplicationManager.getApplication()
				.getService(Preferences.class)
				.accountsDirectory();

		if (projectDirectory.toAbsolutePath().normalize().equals(global.toAbsolutePath().normalize()))
			return null;

		return global;
	}

	@Override
	public @NotNull Options getState() {
		return new Options(directory, includeGlobal);
	}

	@Override
	public void loadState(@NotNull Options state) {
		directory = state.directory == null ? "" : state.directory;
		includeGlobal = state.includeGlobal;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class Options {
		private @Nullable String directory = "";
		private boolean includeGlobal = true;
	}
}
