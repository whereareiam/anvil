package me.whereareiam.anvil.integration.intellij.account.authentication;

import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.model.CatalogSnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates cancellable account-enrollment operations through a scenario source's build integration.
 */
public interface AccountAuthenticator {
	/**
	 * Creates an unstarted enrollment operation that stores its result in the project account library.
	 *
	 * @param source whose prepared runtime supplies authentication providers
	 * @param accountId project-local account identifier
	 * @param libraryId protocol library that signs the account in and owns it, one of the source's
	 *                  {@link CatalogSnapshot#getAuthenticationLibraries() authentication libraries}; null leaves the
	 *                  choice to the runtime, which takes the project's {@code anvil.protocolLibrary} property, then
	 *                  the sole installed library offering authentication, and fails when several offer it
	 * @param output user-facing authentication instructions and progress
	 * @return caller-owned account-enrollment operation
	 */
	@NotNull AccountEnrollment create(
			@NotNull ScenarioSource source,
			@NotNull String accountId,
			@Nullable String libraryId,
			@NotNull Consumer<String> output
	);
}
