package me.whereareiam.anvil.integration.intellij.account.authentication;

import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;

/**
 * Creates cancellable account-enrollment operations through a scenario source's build integration.
 */
public interface AccountAuthenticator {
	/**
	 * Creates an unstarted enrollment operation that stores its result in the project account library.
	 *
	 * @param source whose prepared runtime supplies authentication providers
	 * @param accountId project-local account identifier
	 * @param output user-facing authentication instructions and progress
	 * @return caller-owned account-enrollment operation
	 */
	@NotNull AccountEnrollment create(
			@NotNull ScenarioSource source,
			@NotNull String accountId,
			@NotNull Consumer<String> output
	);
}
