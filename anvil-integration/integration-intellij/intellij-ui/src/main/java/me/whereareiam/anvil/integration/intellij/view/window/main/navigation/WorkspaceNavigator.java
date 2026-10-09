package me.whereareiam.anvil.integration.intellij.view.window.main.navigation;

import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens a process workspace through the Project view or the local file manager.
 */
@RequiredArgsConstructor
public final class WorkspaceNavigator {
	private static final Logger LOG = Logger.getInstance(WorkspaceNavigator.class);
	private final @NotNull Project project;

	/**
	 * Refreshes the directory on a worker and delivers navigation and diagnostics on the IDE event thread.
	 * Disposing the view owner prevents a pending lookup from opening a location or updating the view.
	 *
	 * @param directory process workspace path
	 * @param owner view lifetime for navigation and status delivery
	 * @param status UI-thread callback for progress and failures
	 */
	@RequiresEdt
	public void open(
			@NotNull String directory,
			@NotNull Disposable owner,
			@NotNull Consumer<String> status
	) {
		ApplicationManager.getApplication().assertIsDispatchThread();
		if (project.isDisposed()) return;
		CheckedDisposable request = Disposer.newCheckedDisposable("Anvil workspace navigation");
		if (!Disposer.tryRegister(owner, request)) return;

		status.accept("Locating workspace…");
		Path path;
		try {
			path = Path.of(directory);
		} catch (InvalidPathException failure) {
			status.accept("The workspace path is not valid on this computer.");
			Disposer.dispose(request);
			return;
		}

		CompletableFuture.supplyAsync(() -> LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path),
				task -> ApplicationManager.getApplication().executeOnPooledThread(task))
				.whenComplete((file, failure) -> ApplicationManager.getApplication().invokeLater(
						() -> located(file, failure, request, status), ModalityState.defaultModalityState()));
	}

	private void located(
			@Nullable VirtualFile file,
			@Nullable Throwable failure,
			@NotNull CheckedDisposable request,
			@NotNull Consumer<String> status
	) {
		if (project.isDisposed() || request.isDisposed()) return;
		if (failure != null) {
			LOG.warn("Could not locate Anvil workspace", failure);
			status.accept("Could not locate this workspace. Check that the directory is accessible.");
			Disposer.dispose(request);
			return;
		}
		if (file == null || !file.isValid() || !file.isDirectory()) {
			status.accept("Workspace is no longer available. Anvil may have cleaned it up after the run.");
			Disposer.dispose(request);
			return;
		}

		FileSelectInContext context = new FileSelectInContext(project, file);
		ReadAction.nonBlocking(() -> selectInTarget(file, context))
				.expireWith(project)
				.expireWith(request)
				.finishOnUiThread(ModalityState.defaultModalityState(), target -> select(file, context, target, status))
				.submit(AppExecutorUtil.getAppExecutorService())
				.onProcessed(target -> Disposer.dispose(request));
	}

	private @Nullable SelectInTarget selectInTarget(@NotNull VirtualFile file, @NotNull FileSelectInContext context) {
		ProjectView view = ProjectView.getInstance(project);
		boolean excluded = ProjectFileIndex.getInstance(project).isExcluded(file);
		return view.getSelectInTargets().stream()
				.filter(candidate -> candidate.isAvailable(project))
				.filter(candidate -> !excluded || view.isShowExcludedFiles(candidate.getMinorViewId()))
				.filter(candidate -> candidate.canSelect(context))
				.findFirst()
				.orElse(null);
	}

	private void select(
			@NotNull VirtualFile file,
			@NotNull FileSelectInContext context,
			@Nullable SelectInTarget target,
			@NotNull Consumer<String> status
	) {
		if (target != null) {
			target.selectIn(context, true);
			status.accept("Opened workspace in Project.");
			return;
		}
		if (!RevealFileAction.isDirectoryOpenSupported()) {
			status.accept("This workspace is outside the visible project. Copy its path from the link to open it.");
			return;
		}

		RevealFileAction.openDirectory(file.toNioPath());
		status.accept("Opened workspace in the file manager.");
	}
}
