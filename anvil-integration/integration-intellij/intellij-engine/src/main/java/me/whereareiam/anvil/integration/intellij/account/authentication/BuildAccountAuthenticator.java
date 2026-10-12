package me.whereareiam.anvil.integration.intellij.account.authentication;

import com.intellij.openapi.project.Project;

import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import org.jetbrains.annotations.NotNull;

/**
 * Creates deferred account-enrollment operations using the selected scenario source's preparation
 * instructions.
 */
@RequiredArgsConstructor
public final class BuildAccountAuthenticator implements AccountAuthenticator {
	private final @NotNull Project project;

	@Override
	public @NotNull AccountEnrollment create(
			@NotNull ScenarioSource source,
			@NotNull String accountId,
			@NotNull Consumer<String> output
	) {
		return new ToolingAccountEnrollment(
				() -> project.getService(BuildIntegrations.class).prepare(source),
				project.getService(AccountLibrary.class).directory(),
				accountId,
				output);
	}
}
