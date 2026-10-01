package me.whereareiam.anvil.integration.intellij.view.window.main.navigation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.swing.JList;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;

/**
 * Locates scenario-definition declarations through the project index and opens their source.
 */
@RequiredArgsConstructor
public final class DefinitionNavigator {
	private static final Logger LOG = Logger.getInstance(DefinitionNavigator.class);
	private final @NotNull Project project;

	/**
	 * Resolves source under a smart read action without blocking the UI while IntelliJ indexes the
	 * project. Duplicate declarations prefer the selected scenario source, then offer a source chooser
	 * if still ambiguous.
	 *
	 * @param source     selected scenario source
	 * @param definition fully qualified scenario-definition class name
	 * @param owner      UI lifetime that cancels pending navigation when disposed
	 * @param status     UI-thread callback for progress and lookup or navigation failures
	 */
	@RequiresEdt
	public void open(
			@NotNull ScenarioSource source,
			@NotNull String definition,
			@NotNull Disposable owner,
			@NotNull Consumer<String> status
	) {
		ApplicationManager.getApplication().assertIsDispatchThread();
		if (project.isDisposed()) return;
		CheckedDisposable request = Disposer.newCheckedDisposable("Anvil definition navigation");
		if (!Disposer.tryRegister(owner, request)) return;

		status.accept(
				DumbService.isDumb(project)
						? "Waiting for indexing to locate " + definition + "…"
						: "Locating source for " + definition + "…");
		ReadAction.nonBlocking(() -> findTargets(source.getDirectory(), definition))
				.inSmartMode(project)
				.withDocumentsCommitted(project)
				.expireWith(project)
				.expireWith(request)
				.coalesceBy(this)
				.finishOnUiThread(
						ModalityState.defaultModalityState(),
						sources -> show(sources, definition, request, status))
				.submit(AppExecutorUtil.getAppExecutorService())
				.onError(failure -> failed(definition, request, status, failure));
	}

	private void failed(
			@NotNull String definition,
			@NotNull CheckedDisposable request,
			@NotNull Consumer<String> status,
			@NotNull Throwable failure
	) {
		if (failure instanceof CancellationException) {
			Disposer.dispose(request);
			return;
		}

		LOG.warn("Could not locate Anvil scenario definition source: " + definition, failure);
		ApplicationManager.getApplication().invokeLater(() -> {
			if (!project.isDisposed() && !request.isDisposed())
				status.accept("Could not locate " + definition + ": " + failure.getMessage());
			Disposer.dispose(request);
		}, ModalityState.defaultModalityState());
	}

	private @NotNull List<Target> findTargets(
			@NotNull Path sourceDirectory, @NotNull String definition
	) {
		ApplicationManager.getApplication().assertReadAccessAllowed();
		Map<String, Target> sources = new TreeMap<>();
		ProjectFileIndex index = ProjectFileIndex.getInstance(project);
		for (PsiClass candidate :
				JavaPsiFacade.getInstance(project)
						.findClasses(definition, GlobalSearchScope.projectScope(project))) {
			PsiElement declaration = candidate.getNavigationElement();
			PsiFile containingFile = declaration.getContainingFile();
			if (containingFile == null) continue;
			VirtualFile file = containingFile.getVirtualFile();
			if (file == null || !index.isInSourceContent(file)) continue;

			sources.put(file.getUrl(), new Target(file, declaration.getTextOffset()));
		}
		List<Target> matches = List.copyOf(sources.values());
		List<Target> selectedSource =
				matches.stream()
						.filter(
								target ->
										FileUtil.isAncestor(sourceDirectory.toString(), target.file().getPath(), false))
						.toList();

		return selectedSource.isEmpty() ? matches : selectedSource;
	}

	private void show(
			@NotNull List<Target> sources,
			@NotNull String definition,
			@NotNull CheckedDisposable request,
			@NotNull Consumer<String> status
	) {
		if (project.isDisposed() || request.isDisposed()) return;

		if (sources.isEmpty()) {
			status.accept(
					"Source not found for "
							+ definition
							+ ". Check that its IntelliJ module and source roots are imported.");
			Disposer.dispose(request);
			return;
		}
		if (sources.size() == 1) {
			navigate(sources.getFirst(), definition, request, status);
			return;
		}

		status.accept(
				"Choose one of " + sources.size() + " source declarations for " + definition + ".");
		var chooser =
				JBPopupFactory.getInstance()
						.createPopupChooserBuilder(sources)
						.setTitle("Choose Definition Source")
						.setRenderer(
								new SimpleListCellRenderer<>() {
									@Override
									public void customize(
											@NotNull JList<? extends Target> list,
											Target target,
											int index,
											boolean selected,
											boolean focused) {
										setText(target == null ? "" : target.file().getPresentableUrl());
									}
								})
						.setItemChosenCallback(target -> navigate(target, definition, request, status))
						.addListener(new JBPopupListener() {
							@Override
							public void onClosed(@NotNull LightweightWindowEvent event) {
								if (!event.isOk()) Disposer.dispose(request);
							}
						})
						.createPopup();
		Disposer.register(request, chooser);
		chooser.showInFocusCenter();
	}

	private void navigate(
			@NotNull Target source,
			@NotNull String definition,
			@NotNull CheckedDisposable request,
			@NotNull Consumer<String> status
	) {
		if (project.isDisposed() || request.isDisposed()) return;

		try {
			if (!source.file().isValid()) {
				status.accept("Source for " + definition + " was removed. Refresh scenarios and try again.");
				return;
			}
			var descriptor = new OpenFileDescriptor(project, source.file(), source.offset());
			if (!descriptor.navigateInEditor(project, true)) {
				status.accept("IntelliJ could not open " + source.file().getPresentableUrl() + ".");
				return;
			}

			status.accept("Opened " + source.file().getPresentableUrl());
		} finally {
			Disposer.dispose(request);
		}
	}

	private record Target(@NotNull VirtualFile file, int offset) {}

}
