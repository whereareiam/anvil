package me.whereareiam.anvil.integration.intellij.account;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
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
import me.whereareiam.anvil.integration.intellij.account.settings.AccountSettings;
import me.whereareiam.anvil.integration.intellij.account.storage.AccountDirectoryRepository;
import me.whereareiam.anvil.integration.intellij.account.storage.AccountPoolRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The accounts and pools one project can use: its own account directory, optionally merged with the global one.
 * <p>
 * The project directory override is personal and path-specific, so it is stored in the workspace file rather
 * than in shared project configuration. The global directory belongs to {@link AccountSettings}.
 */
@Service(Service.Level.PROJECT)
@RequiredArgsConstructor
@State(name = "AnvilProjectAccounts", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class AccountLibrary implements PersistentStateComponent<AccountLibrary.Options> {
	private final AccountDirectoryRepository accounts = new AccountDirectoryRepository();
	private final @NotNull Project project;

	private @NotNull String directory = "";
	private boolean includeGlobal = true;

	/**
	 * Returns the library of the given project.
	 */
	public static @NotNull AccountLibrary getInstance(@NotNull Project project) {
		return project.getService(AccountLibrary.class);
	}

	/**
	 * Returns the configured project account directory, or {@link #defaultDirectory()} when none is set.
	 */
	public @NotNull Path directory() {
		return directory.isBlank() ? defaultDirectory() : Path.of(directory);
	}

	/**
	 * Returns the project's default account directory. It lies outside the project tree, so credential files
	 * cannot be committed with the project; the location hash keeps each project's accounts separate.
	 */
	public @NotNull Path defaultDirectory() {
		return Path.of(System.getProperty("user.home"), ".anvil", "projects", project.getLocationHash(), "accounts");
	}

	/**
	 * Stores an absolute normalized project account directory. Storing the default directory clears the
	 * override, so the project keeps following the default.
	 */
	public void setDirectory(@NotNull Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		directory = normalized.equals(defaultDirectory()) ? "" : normalized.toString();
	}

	/**
	 * Reports whether the global account directory takes part in this project.
	 */
	public boolean includesGlobal() {
		return includeGlobal;
	}

	/**
	 * Controls whether the global account directory takes part in this project.
	 */
	public void setIncludesGlobal(boolean include) {
		includeGlobal = include;
	}

	/**
	 * Returns the account directories this project reads, the project directory first.
	 */
	public @NotNull List<Path> roots() {
		Path project = directory().toAbsolutePath().normalize();
		Path global = globalDirectory(project);

		return global == null ? List.of(project) : List.of(project, global);
	}

	/**
	 * Reads every available account and the project's pools without exposing credentials.
	 * <p>
	 * Problems that would make a run refuse its accounts, such as an account ID or pool defined in both
	 * directories, are reported in {@link AccountCatalog#getProblems()} rather than thrown.
	 */
	public @NotNull AccountCatalog catalog() {
		Path projectDirectory = directory();
		Path globalDirectory = globalDirectory(projectDirectory);
		List<AvailableAccount> available = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		read(projectDirectory, AccountSource.PROJECT, available, problems);
		if (globalDirectory != null) read(globalDirectory, AccountSource.GLOBAL, available, problems);

		available.sort(Comparator.comparing(account -> account.getAccount().getAccountId()));
		addDuplicateProblems(available, problems);

		Map<String, List<String>> projectPools = readPools(projectDirectory, problems);
		if (globalDirectory != null) {
			Map<String, List<String>> globalPools = readPools(globalDirectory, problems);
			for (String name : projectPools.keySet())
				if (globalPools.containsKey(name))
					problems.add("Pool '" + name + "' also exists globally. Rename the project pool or disable global"
							+ " accounts before running.");
		}

		return AccountCatalog.builder()
				.accounts(List.copyOf(available))
				.pools(projectPools)
				.problems(List.copyOf(problems))
				.build();
	}

	/**
	 * Validates an account file and reads only its non-secret metadata.
	 *
	 * @throws IOException if the file is unreadable or unsupported
	 */
	public @NotNull AuthenticationAccount inspect(@NotNull Path source) throws IOException {
		return accounts.readMetadata(source);
	}

	/**
	 * Validates an account file and copies it privately into the project account directory.
	 *
	 * @throws IOException if validation or copying fails
	 */
	public @NotNull AuthenticationAccount importAccount(@NotNull Path source) throws IOException {
		return accounts.importAccount(source, directory());
	}

	/**
	 * Copies an account's credential file to a destination the user chose.
	 *
	 * @throws IOException if copying fails
	 */
	public void exportAccount(@NotNull AvailableAccount account, @NotNull Path destination) throws IOException {
		accounts.exportAccount(account, destination);
	}

	/**
	 * Removes an account owned by this project. Global accounts cannot be removed here.
	 *
	 * @throws IOException if the account is global or the file cannot be deleted
	 */
	public void removeAccount(@NotNull AvailableAccount account) throws IOException {
		accounts.removeAccount(account);
	}

	/**
	 * Reports whether an ID is safe to use as an account file name.
	 */
	public boolean isValidAccountId(@NotNull String id) {
		return accounts.isSafeAccountId(id);
	}

	/**
	 * Creates or renames a project pool as a stable list of account IDs. Like the runtime, it refuses an account
	 * ID that several protocol libraries store, because a pool leases one account per ID.
	 *
	 * @param previous original pool name, or null for a new pool
	 * @throws IOException if the pool is invalid, conflicts with another pool, or cannot be written
	 */
	public void savePool(@Nullable String previous, @NotNull String name, @NotNull List<String> accountIds)
			throws IOException {
		List<AuthenticationAccount> known = catalog().getAccounts().stream().map(AvailableAccount::getAccount).toList();
		new AccountPoolRepository(directory()).save(previous, name, accountIds, known);
	}

	/**
	 * Removes a project pool without deleting its accounts.
	 *
	 * @throws IOException if the pool file cannot be updated
	 */
	public void removePool(@NotNull String name) throws IOException {
		new AccountPoolRepository(directory()).remove(name);
	}

	private void read(
			@NotNull Path directory,
			@NotNull AccountSource source,
			@NotNull List<AvailableAccount> available,
			@NotNull List<String> problems
	) {
		AccountDirectoryRepository.Listing listing = accounts.readAccounts(directory, source);
		available.addAll(listing.accounts());
		problems.addAll(listing.problems());
	}

	private static void addDuplicateProblems(@NotNull List<AvailableAccount> available, @NotNull List<String> problems) {
		Set<String> ids = new HashSet<>();
		for (AvailableAccount account : available) {
			String id = account.getAccount().getAccountId();
			if (!ids.add(id))
				problems.add("Duplicate account ID '" + id + "'. Remove the project duplicate or disable global"
						+ " accounts before running.");
		}
	}

	private static @NotNull Map<String, List<String>> readPools(@NotNull Path directory, @NotNull List<String> problems) {
		try {
			return new AccountPoolRepository(directory).read();
		} catch (IOException failure) {
			problems.add("Cannot read account pools: " + failure.getMessage());
			return Map.of();
		}
	}

	private @Nullable Path globalDirectory(@NotNull Path projectDirectory) {
		if (!includeGlobal) return null;

		Path global = AccountSettings.getInstance().directory().toAbsolutePath().normalize();
		if (projectDirectory.toAbsolutePath().normalize().equals(global)) return null;

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

	/**
	 * Workspace state: the project directory override, blank for the default, and global participation.
	 */
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class Options {
		private @Nullable String directory = "";
		private boolean includeGlobal = true;
	}
}
