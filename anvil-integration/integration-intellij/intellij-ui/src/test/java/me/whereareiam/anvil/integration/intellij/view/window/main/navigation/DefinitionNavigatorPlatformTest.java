package me.whereareiam.anvil.integration.intellij.view.window.main.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.Consumer;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;

public class DefinitionNavigatorPlatformTest extends UiPlatformTestCase {
	public void testOpensIndexedDefinitionFromAsyncReadAction() {
		PsiFile provider =
				myFixture.addFileToProject(
						"example/Catalog.java", "package example; public class Catalog {}\n");
		List<String> statuses = new ArrayList<>();
		var navigator = new DefinitionNavigator(getProject());

		navigator.open(
				source(provider.getVirtualFile().getParent().getParent()),
				"example.Catalog",
				getTestRootDisposable(),
				message -> {
					assertTrue(
							"Navigation status must stay on the UI thread",
							ApplicationManager.getApplication().isDispatchThread());
					statuses.add(message);
				});
		awaitFinished(statuses);

		assertTrue(statuses.getFirst().startsWith("Locating source"));
		assertTrue(statuses.toString(), statuses.getLast().startsWith("Opened "));
		assertEquals(
				provider.getVirtualFile(),
				FileEditorManager.getInstance(getProject()).getSelectedFiles()[0]);
		assertEquals(
				"Navigation must land on the class declaration",
				provider.getText().indexOf("Catalog"),
				FileEditorManager.getInstance(getProject())
						.getSelectedTextEditor()
						.getCaretModel()
						.getOffset());
	}

	public void testFindsDefinitionInImportedTestSourceRoot() throws Exception {
		VirtualFile tests =
				myFixture.getTempDirFixture().findOrCreateDir("testing-server/src/test/java");
		PsiTestUtil.addSourceRoot(getModule(), tests, true);
		PsiFile provider =
				myFixture.addFileToProject(
						"testing-server/src/test/java/example/TestCatalog.java",
						"package example; public class TestCatalog {}\n");
		assertTrue(
				ProjectFileIndex.getInstance(getProject())
						.isInTestSourceContent(provider.getVirtualFile()));

		open(source(tests.getParent().getParent().getParent()), "example.TestCatalog", provider.getVirtualFile());
	}

	public void testDuplicateDefinitionsPreferSelectedModuleAndOtherwiseKeepSortedChoices()
			throws Exception {
		VirtualFile firstRoot = myFixture.getTempDirFixture().findOrCreateDir("first/src");
		VirtualFile secondRoot = myFixture.getTempDirFixture().findOrCreateDir("second/src");
		PsiTestUtil.addSourceRoot(getModule(), firstRoot);
		PsiTestUtil.addSourceRoot(getModule(), secondRoot);
		PsiFile first =
				myFixture.addFileToProject(
						"first/src/example/Catalog.java", "package example; public class Catalog {}\n");
		PsiFile second =
				myFixture.addFileToProject(
						"second/src/example/Catalog.java", "package example; public class Catalog {}\n");

		open(source(secondRoot.getParent()), "example.Catalog", second.getVirtualFile());
		FileEditorManager.getInstance(getProject()).closeFile(second.getVirtualFile());
		var popups = new CapturedPopups();
		ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), JBPopupFactory.class,
				popups, getTestRootDisposable());
		List<String> statuses = new ArrayList<>();
		new DefinitionNavigator(getProject()).open(
				source(firstRoot).toBuilder().directory(Path.of("/another-module")).build(),
				"example.Catalog", getTestRootDisposable(), statuses::add);
		PlatformTestUtil.waitWithEventsDispatching(() -> "Definition chooser did not appear", () -> popups.shown, 10);

		var expected = List.of(first.getVirtualFile(), second.getVirtualFile()).stream()
				.sorted(Comparator.comparing(VirtualFile::getUrl)).toList();
		assertEquals(expected.stream().map(VirtualFile::getPresentableUrl).toList(), popups.choices);
		assertEquals(0, FileEditorManager.getInstance(getProject()).getSelectedFiles().length);
		popups.chooseSecond.run();
		assertEquals(expected.get(1), FileEditorManager.getInstance(getProject()).getSelectedFiles()[0]);
		assertTrue(statuses.getLast().startsWith("Opened "));
	}

	public void testMissingSourceReportsActionableStatus() {
		List<String> statuses = new ArrayList<>();
		new DefinitionNavigator(getProject())
				.open(
						ScenarioSource.builder()
								.id("absent")
								.displayName("Absent")
								.integrationId("fixture")
								.directory(Path.of("/absent"))
								.build(),
						"example.MissingCatalog",
						getTestRootDisposable(),
						statuses::add);
		awaitFinished(statuses);

		assertTrue(statuses.getLast().contains("Source not found for example.MissingCatalog"));
			assertTrue(statuses.getLast().contains("IntelliJ module and source roots"));
		assertEquals(0, FileEditorManager.getInstance(getProject()).getSelectedFiles().length);
	}

	public void testWaitsForIndexingBeforeOpeningDefinition() throws Throwable {
		PsiFile provider =
				myFixture.addFileToProject(
						"example/Catalog.java", "package example; public class Catalog {}\n");
		List<String> statuses = new ArrayList<>();
		DumbModeTestUtils.runInDumbModeSynchronously(
				getProject(),
				() -> {
					new DefinitionNavigator(getProject())
							.open(
									source(provider.getVirtualFile().getParent().getParent()),
									"example.Catalog",
									getTestRootDisposable(),
									statuses::add);
					assertEquals(1, statuses.size());
					assertTrue(statuses.getFirst().startsWith("Waiting for indexing"));
					assertEquals(0, FileEditorManager.getInstance(getProject()).getSelectedFiles().length);
				});
		awaitFinished(statuses);

		assertTrue(statuses.toString(), statuses.getLast().startsWith("Opened "));
	}

	public void testDisposedOwnerCancelsPendingIndexLookup() throws Throwable {
		PsiFile provider =
				myFixture.addFileToProject(
						"example/Catalog.java", "package example; public class Catalog {}\n");
		List<String> statuses = new ArrayList<>();
		var owner = Disposer.newDisposable();
		Disposer.register(getTestRootDisposable(), owner);
		DumbModeTestUtils.runInDumbModeSynchronously(
				getProject(),
				() -> {
					new DefinitionNavigator(getProject())
							.open(
									source(provider.getVirtualFile().getParent().getParent()),
									"example.Catalog",
									owner,
									statuses::add);
					Disposer.dispose(owner);
				});
		PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

		assertEquals(1, statuses.size());
		assertEquals(0, FileEditorManager.getInstance(getProject()).getSelectedFiles().length);
	}

	private void open(ScenarioSource source, String definition, VirtualFile expected) {
		List<String> statuses = new ArrayList<>();
		new DefinitionNavigator(getProject()).open(source, definition, getTestRootDisposable(), statuses::add);
		awaitFinished(statuses);
		assertTrue(statuses.toString(), statuses.getLast().startsWith("Opened "));
		assertEquals(expected, FileEditorManager.getInstance(getProject()).getSelectedFiles()[0]);
	}

	private ScenarioSource source(VirtualFile directory) {
		return ScenarioSource.builder()
				.id(directory.getPath())
				.displayName(directory.getName())
				.integrationId("fixture")
				.directory(Path.of(directory.getPath()))
				.build();
	}

	private void awaitFinished(List<String> statuses) {
		PlatformTestUtil.waitWithEventsDispatching(
				() -> "Source lookup did not finish: " + statuses, () -> statuses.size() > 1, 10);
	}
	/**
	 * Captures the native chooser through its public SDK interfaces without displaying an OS window.
	 */
	private static final class CapturedPopups extends PopupFactoryImpl {
		private final List<String> choices = new ArrayList<>();
		private boolean shown;
		private Runnable chooseSecond;

		@Override
		@SuppressWarnings("unchecked")
		public <T> IPopupChooserBuilder<T> createPopupChooserBuilder(List<? extends T> items) {
			IPopupChooserBuilder<T> delegate = super.createPopupChooserBuilder(items);
			return (IPopupChooserBuilder<T>) Proxy.newProxyInstance(IPopupChooserBuilder.class.getClassLoader(),
					new Class<?>[] {IPopupChooserBuilder.class}, (proxy, method, arguments) -> {
				if (method.getName().equals("setRenderer")) {
					ListCellRenderer<? super T> renderer = (ListCellRenderer<? super T>) arguments[0];
					JList<T> list = new JList<>();
					for (int index = 0; index < items.size(); index++) {
						var label = (JLabel) renderer.getListCellRendererComponent(list, items.get(index), index, false, false);
						choices.add(label.getText());
					}
				}
				if (method.getName().equals("setItemChosenCallback")) {
					Consumer<? super T> callback = (Consumer<? super T>) arguments[0];
					chooseSecond = () -> callback.consume(items.get(1));
				}
				Object result = method.invoke(delegate, arguments);
				if (!(result instanceof JBPopup popup)) return result == delegate ? proxy : result;

				return Proxy.newProxyInstance(JBPopup.class.getClassLoader(), new Class<?>[] {JBPopup.class},
						(popupProxy, operation, values) -> {
					if (operation.getName().equals("showInFocusCenter")) {
						shown = true;
						return null;
					}
					return operation.invoke(popup, values);
				});
			});
		}
	}

}
