package me.whereareiam.anvil.integration.intellij.gradle.sync;

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;

import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Publishes native import outcomes only for builds linked to the owning IDE project.
 */
@RequiredArgsConstructor
public final class ModuleImportListener implements ProjectDataImportListener {
	private final @NotNull GradleSettings settings;
	private final @NotNull Consumer<ProjectChange> listener;

	@Override
	public void onImportFinished(@Nullable String projectPath) {
		publish(projectPath, ProjectChange.IMPORTED);
	}

	@Override
	public void onImportFailed(@Nullable String projectPath, @NotNull Throwable failure) {
		publish(projectPath, ProjectChange.IMPORT_FAILED);
	}

	private void publish(@Nullable String projectPath, @NotNull ProjectChange change) {
		if (projectPath == null) return;
		if (settings.getLinkedProjectSettings(projectPath) == null) return;

		listener.accept(change);
	}
}
