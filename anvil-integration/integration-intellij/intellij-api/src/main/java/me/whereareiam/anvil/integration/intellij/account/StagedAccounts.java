package me.whereareiam.anvil.integration.intellij.account;

import java.io.IOException;
import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;

/**
 * A temporary copy of a project's accounts, created by {@link AccountStaging#stage()}.
 */
public interface StagedAccounts extends AutoCloseable {
	/**
	 * Returns the directory holding the copied account files and the merged pool definitions.
	 */
	@NotNull Path directory();

	/**
	 * Deletes the directory and every copied credential in it.
	 *
	 * @throws IOException if a file cannot be deleted; the remaining files are still attempted
	 */
	@Override
	void close() throws IOException;
}
