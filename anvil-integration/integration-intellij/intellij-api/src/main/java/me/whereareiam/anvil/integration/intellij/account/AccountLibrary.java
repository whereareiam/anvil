package me.whereareiam.anvil.integration.intellij.account;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.integration.intellij.model.account.AccountCatalog;
import me.whereareiam.anvil.integration.intellij.model.account.AvailableAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-scoped account locations, available credentials, and named account pools.
 */
public interface AccountLibrary {
	/**
	 * Returns the configured project account directory, or {@link #defaultDirectory()} when none is set.
	 */
	@NotNull Path directory();

	/**
	 * Returns the project's default account directory. It lies outside the project tree, so credential
	 * files cannot be committed with the project.
	 */
	@NotNull Path defaultDirectory();

	/**
	 * Stores an absolute normalized project account directory. Storing the default directory clears the
	 * override, so the project keeps following the default.
	 */
	void setDirectory(@NotNull Path directory);

	/**
	 * Reports whether the global account directory participates in this project.
	 */
	boolean includesGlobal();

	/**
	 * Controls whether the global account directory participates in this project.
	 */
	void setIncludesGlobal(boolean include);

	/**
	 * Reads the project account catalog and optional global source without exposing credentials.
	 */
	@NotNull AccountCatalog catalog();

	/**
	 * Validates an account file and reads only its non-secret metadata.
	 *
	 * @throws IOException if the file is unreadable or unsupported
	 */
	@NotNull AuthenticationAccount inspect(@NotNull Path source) throws IOException;

	/**
	 * Validates and privately copies an account into the project account directory.
	 *
	 * @throws IOException if validation or copying fails
	 */
	@NotNull AuthenticationAccount importAccount(@NotNull Path source) throws IOException;

	/**
	 * Exports the selected opaque credential file to an explicit destination.
	 *
	 * @throws IOException if copying fails
	 */
	void exportAccount(
			@NotNull AvailableAccount account,
			@NotNull Path destination
	) throws IOException;

	/**
	 * Removes an account owned by this project. Global accounts cannot be removed here.
	 *
	 * @throws IOException if removal is disallowed or fails
	 */
	void removeAccount(@NotNull AvailableAccount account) throws IOException;

	/**
	 * Reports whether an ID is safe to use as an account filename.
	 */
	boolean isValidAccountId(@NotNull String id);

	/**
	 * Creates or renames a project pool as a stable list of account IDs. Like the runtime, it refuses an
	 * account ID that several protocol libraries store among the configured accounts, because a pool leases
	 * one account per ID.
	 *
	 * @param previous original pool name, or null for a new pool
	 * @throws IOException if the pool is invalid, lists an account ID that several protocol libraries store,
	 * conflicts, or cannot be written
	 */
	void savePool(
			@Nullable String previous,
			@NotNull String name,
			@NotNull List<String> accountIds
	) throws IOException;

	/**
	 * Removes a project pool without deleting its accounts.
	 *
	 * @throws IOException if the pool file cannot be updated
	 */
	void removePool(@NotNull String name) throws IOException;
}
