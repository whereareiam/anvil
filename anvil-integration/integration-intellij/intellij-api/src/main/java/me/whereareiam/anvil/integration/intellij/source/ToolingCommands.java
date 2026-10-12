package me.whereareiam.anvil.integration.intellij.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the JVM commands that run the tooling runtime a source's preparation wrote into its manifest.
 * <p>
 * A build integration's preparation task writes a launch manifest naming the Java executable, the runtime
 * class path, and the scenario definitions. This application service reads it, so features start the runner
 * or sign in an account without knowing the manifest's format.
 */
public interface ToolingCommands {
	/**
	 * Builds the command that starts the tooling runner for the prepared scenarios.
	 *
	 * @param manifest launch manifest written by the source's preparation
	 * @param accountsDirectory directory of staged accounts the runner may lease, or null for none
	 * @return the complete command line
	 * @throws IOException if the manifest is unreadable or was written by an unsupported Anvil version
	 */
	@NotNull List<String> runner(@NotNull Path manifest, @Nullable Path accountsDirectory) throws IOException;

	/**
	 * Builds the command that signs in one account through the prepared runtime's protocol library.
	 *
	 * @param manifest launch manifest written by the source's preparation
	 * @param accountsDirectory directory that receives the signed-in account
	 * @param accountId file-safe identifier of the account to create or replace
	 * @return the complete command line
	 * @throws IOException if the manifest is unreadable or was written by an unsupported Anvil version
	 */
	@NotNull List<String> authentication(
			@NotNull Path manifest,
			@NotNull Path accountsDirectory,
			@NotNull String accountId
	) throws IOException;
}
