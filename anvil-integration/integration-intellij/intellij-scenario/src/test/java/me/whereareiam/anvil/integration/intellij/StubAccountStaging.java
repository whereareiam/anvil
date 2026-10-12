package me.whereareiam.anvil.integration.intellij;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.whereareiam.anvil.integration.intellij.account.AccountStaging;
import me.whereareiam.anvil.integration.intellij.account.StagedAccounts;
import org.jetbrains.annotations.NotNull;

/**
 * Stages an empty account directory, standing in for the account feature in scenario tests.
 */
public final class StubAccountStaging implements AccountStaging {
	@Override
	public @NotNull StagedAccounts stage() throws IOException {
		Path directory = Files.createTempDirectory("anvil-test-accounts-");
		return new StagedAccounts() {
			@Override
			public @NotNull Path directory() {
				return directory;
			}

			@Override
			public void close() throws IOException {
				Files.deleteIfExists(directory);
			}
		};
	}
}
