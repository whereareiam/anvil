package me.whereareiam.anvil.integration.intellij.gradle.sync;

import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

@RequiredArgsConstructor
final class SyncOperation implements ProjectDataImportListener, ExternalSystemTaskNotificationListener {
	private final @NotNull Project project;
	private final @NotNull String linkedPath;
	private final @Nullable BiConsumer<String, ImportSpec> importer;
	private final @NotNull CompletableFuture<Void> result = new CompletableFuture<>();
	private final @NotNull SyncFailureBuilder failures = new SyncFailureBuilder();
	private final @NotNull AtomicReference<Throwable> nativeFailure = new AtomicReference<>();
	private final @NotNull AtomicReference<ExternalSystemTaskId> observedTask = new AtomicReference<>();
	private final @NotNull AtomicBoolean observing = new AtomicBoolean();
	private final @NotNull AtomicBoolean imported = new AtomicBoolean();
	private final @NotNull Disposable lifetime = Disposer.newDisposable("Anvil project sync");

	@Nullable CompletableFuture<Void> start() {
		Disposer.register(project, lifetime);
		Disposer.register(lifetime, () -> result.completeExceptionally(new CancellationException("Project sync stopped.")));
		result.whenComplete((ignored, failure) -> Disposer.dispose(lifetime));
		project.getMessageBus().connect(lifetime).subscribe(ProjectDataImportListener.TOPIC, this);

		ExternalSystemProgressNotificationManager.getInstance()
				.addNotificationListener(this, lifetime);

		try {
			if (result.isDone()) return result;

			ExternalSystemTask active = activeTask();
			if (active != null || imported.get() || nativeFailure.get() != null) {
				observe(active);
				return result;
			}

			if (importer == null) {
				Disposer.dispose(lifetime);
				return null;
			}

			requestImport();
		} catch (RuntimeException failure) {
			result.completeExceptionally(failure);
		}

		return result;
	}

	@Override
	public void onImportFinished(@Nullable String projectPath) {
		if (!matchesPath(projectPath)) return;

		imported.set(true);
		if (observing.get()) result.complete(null);
	}

	@Override
	public void onImportFailed(@Nullable String projectPath, @NotNull Throwable failure) {
		if (matchesPath(projectPath)) result.completeExceptionally(failures.build(
				linkedPath,
				failure.getMessage(),
				null,
				failure
		));
	}

	@Override
	public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
		if (matches(projectPath, id)) observedTask.set(id);
	}

	@Override
	public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
		if (event.getId().equals(observedTask.get())
				&& event instanceof ExternalSystemBuildEvent build
				&& build.getBuildEvent() instanceof MessageEvent message) failures.add(message);
	}

	@Override
	public void onFailure(
			@NotNull String projectPath,
			@NotNull ExternalSystemTaskId id,
			@NotNull Exception failure
	) {
		if (!matches(projectPath, id)) return;

		nativeFailure.set(failure);
		if (observing.get()) result.completeExceptionally(failures.build(
				linkedPath,
				failure.getMessage(),
				null,
				failure
		));
	}

	@Override
	public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
		if (matches(projectPath, id)) {
			result.completeExceptionally(
					new CancellationException("Project sync was cancelled.")
			);
		}
	}

	private void observe(@Nullable ExternalSystemTask active) {
		observing.set(true);
		if (active != null) observedTask.set(active.getId());

		Throwable failure = nativeFailure.get();
		if (failure != null) {
			result.completeExceptionally(failures.build(linkedPath, failure.getMessage(), null, failure));
			return;
		}

		if (imported.get()) result.complete(null);
	}

	private void requestImport() {
		ImportSpec specification = new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
				.withCallback(new RefreshCallback())
				.build();

		if (!result.isDone()) importer.accept(linkedPath, specification);
	}

	private @Nullable ExternalSystemTask activeTask() {
		ExternalSystemTask task = ExternalSystemProcessingManager.getInstance()
				.findTask(ExternalSystemTaskType.RESOLVE_PROJECT, GradleConstants.SYSTEM_ID, linkedPath);

		if (task == null || task.getState().isStopped()) return null;
		return ExternalSystemTaskId.getProjectId(project).equals(task.getId().getIdeProjectId()) ? task : null;
	}

	private boolean matchesPath(@Nullable String projectPath) {
		return projectPath != null
				&& Path.of(projectPath).normalize()
					.equals(Path.of(linkedPath)
					.normalize());
	}

	private boolean matches(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
		return id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT
				&& GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())
				&& ExternalSystemTaskId.getProjectId(project).equals(id.getIdeProjectId())
				&& matchesPath(projectPath);
	}

	private final class RefreshCallback implements ExternalProjectRefreshCallback {
		@Override
		public void onSuccess(@Nullable DataNode<ProjectData> data) {
			result.complete(null);
		}

		@Override
		public void onFailure(@NotNull String message, @Nullable String details) {
			result.completeExceptionally(failures.build(linkedPath, message, details, nativeFailure.get()));
		}
	}
}
