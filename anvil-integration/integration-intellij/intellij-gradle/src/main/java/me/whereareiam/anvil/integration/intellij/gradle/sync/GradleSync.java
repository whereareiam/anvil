package me.whereareiam.anvil.integration.intellij.gradle.sync;

import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Starts or observes one native Gradle project sync.
 */
public final class GradleSync {
	/**
	 * Observes an already-running native sync, if one exists.
	 */
	public static @Nullable CompletableFuture<Void> observe(@NotNull Project project, @NotNull String linkedPath) {
		return request(project, linkedPath, null);
	}

	/**
	 * Requests native sync and completes after IntelliJ imports its model.
	 */
	public static @NotNull CompletableFuture<Void> request(@NotNull Project project, @NotNull String linkedPath) {
		return Objects.requireNonNull(request(project, linkedPath, ExternalSystemUtil::refreshProject));
	}

	static @Nullable CompletableFuture<Void> request(
			@NotNull Project project,
			@NotNull String linkedPath,
			@Nullable BiConsumer<String, ImportSpec> importer
	) {
		return new SyncOperation(project, linkedPath, importer).start();
	}
}
