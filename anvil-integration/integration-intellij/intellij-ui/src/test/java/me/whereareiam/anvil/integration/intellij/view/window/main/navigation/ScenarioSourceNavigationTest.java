package me.whereareiam.anvil.integration.intellij.view.window.main.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.awt.Toolkit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;

public class ScenarioSourceNavigationTest extends HeavyPlatformTestCase {
	@Override
	protected void initApplication() throws Exception {
		// Initialize application-wide desktop services before the fixture tracks project resources.
		Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
		super.initApplication();
	}

	@Override
	protected @NotNull ModuleType<?> getModuleType() {
		return JavaModuleType.getModuleType();
	}

	public void testFindsDefinitionInSeparateImportedModule() throws Exception {
		VirtualFile included = getVirtualFile(createTempDirectory());
		var imported = PsiTestUtil.addModule(getProject(), getModuleType(), "included-build", included);
		VirtualFile sources =
				WriteAction.compute(() -> VfsUtil.createDirectoryIfMissing(included, "src/test/java"));
		PsiTestUtil.addSourceRoot(imported, sources, true);
		VirtualFile provider =
				WriteAction.compute(
						() -> {
							VirtualFile file =
									VfsUtil.createDirectoryIfMissing(sources, "example")
											.createChildData(this, "IncludedCatalog.java");
							VfsUtil.saveText(file, "package example; public class IncludedCatalog {}\n");
							return file;
						});
		IndexingTestUtil.waitUntilIndexesAreReady(getProject());
		assertEquals(
				imported,
				ApplicationManager.getApplication().runReadAction(
						(Computable<Module>) () -> ProjectFileIndex.getInstance(getProject()).getModuleForFile(provider)));
		assertTrue(
				ApplicationManager.getApplication().runReadAction(
						(Computable<Boolean>) () -> ProjectFileIndex.getInstance(getProject()).isInTestSourceContent(provider)));

		List<String> statuses = new ArrayList<>();
		ScenarioSource selected = ScenarioSource.builder().id("included-build").integrationId("fixture")
				.displayName("Included build").directory(Path.of(included.getPath())).build();
		new DefinitionNavigator(getProject()).open(selected, "example.IncludedCatalog", getTestRootDisposable(), statuses::add);
		PlatformTestUtil.waitWithEventsDispatching(() -> "Navigation did not finish: " + statuses, () -> statuses.size() > 1, 10);

		assertTrue(statuses.toString(), statuses.getLast().startsWith("Opened "));
		assertEquals(provider, FileEditorManager.getInstance(getProject()).getSelectedFiles()[0]);
	}
}
