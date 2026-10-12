package me.whereareiam.anvil.integration.intellij.account;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

/**
 * Supplies the accounts a scenario run may use as a private directory that the run owns.
 * <p>
 * The account feature implements this project service. A scenario launch stages the accounts once, hands
 * {@link StagedAccounts#directory()} to the tooling runner, and closes the result after the runner exits,
 * which deletes every copied credential. Persistent account files are never modified.
 */
public interface AccountStaging {
	/**
	 * Copies the accounts and pools configured for the project into a new private directory.
	 *
	 * @return staged accounts that the caller must close
	 * @throws IOException if an account store cannot be read, two stores define the same pool, or copying fails
	 */
	@NotNull StagedAccounts stage() throws IOException;
}
