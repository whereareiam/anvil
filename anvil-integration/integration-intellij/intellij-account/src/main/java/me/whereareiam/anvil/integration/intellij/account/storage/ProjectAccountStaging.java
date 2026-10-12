package me.whereareiam.anvil.integration.intellij.account.storage;

import com.intellij.openapi.project.Project;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.AccountStaging;
import me.whereareiam.anvil.integration.intellij.account.StagedAccounts;
import org.jetbrains.annotations.NotNull;

/**
 * Stages the directories the project's {@link AccountLibrary} reads for one scenario run.
 */
@RequiredArgsConstructor
public final class ProjectAccountStaging implements AccountStaging {
	private final @NotNull Project project;

	@Override
	public @NotNull StagedAccounts stage() throws IOException {
		return StagedAccountDirectory.create(AccountLibrary.getInstance(project).roots());
	}
}
