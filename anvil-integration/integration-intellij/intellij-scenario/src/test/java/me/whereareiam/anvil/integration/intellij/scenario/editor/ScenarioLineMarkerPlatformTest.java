package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ExtensionTestUtil;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.AnvilRunConfiguration;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;

public class ScenarioLineMarkerPlatformTest extends ScenarioPlatformTestCase {
	private static final String API = """
			package me.whereareiam.anvil.api.scenario;
			public interface AnvilScenarioDefinition { Object define(); }
			""";

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		myFixture.addFileToProject("me/whereareiam/anvil/api/scenario/AnvilScenarioDefinition.java", API);
	}

	public void testOnlyConcreteDefinitionClassesGetARunIcon() {
		var definition = type("Registration", """
				package example;
				public class Registration implements me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition {
					public Object define() { return null; }
				}
				""");
		var base = type("Base", """
				package example;
				public abstract class Base implements me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition {}
				""");
		var unrelated = type("Helper", "package example; public class Helper {}");
		var inherited = type("Derived", "package example; public class Derived extends Base { public Object define() { return null; } }");

		listSourceOf(definition);
		var marker = new ScenarioLineMarker();
		assertNotNull(marker.getInfo(identifier(definition)));
		assertTrue("The icon offers the scenario's tests", Arrays.stream(marker.getInfo(identifier(definition)).actions)
				.anyMatch(action -> action == ActionManager.getInstance().getAction("Anvil.RunScenarioTests")));
		assertNotNull("A class inherits the definition contract", marker.getInfo(identifier(inherited)));
		assertNull(marker.getInfo(identifier(base)));
		assertNull(marker.getInfo(identifier(unrelated)));
	}

	public void testRunFromTheClassCreatesAConfigurationForItsSourceAndDefinition() {
		var definition = type("Registration", """
				package example;
				public class Registration implements me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition {
					public Object define() { return null; }
				}
				""");
		listSourceOf(definition);

		var context = ConfigurationContext.createEmptyContextForLocation(
				com.intellij.execution.PsiLocation.fromPsiElement(identifier(definition)));
		var created = context.getConfiguration();

		assertNotNull(created);
		var configuration = (AnvilRunConfiguration) created.getConfiguration();
		assertEquals("fixture:main", configuration.getSourceId());
		assertEquals("example.Registration", configuration.getDefinition());
		assertEquals("", configuration.getProcessId());
		assertEquals("Registration", created.getName());
	}

	public void testClassesOutsideAnAnvilSourceGetNoRunIcon() {
		var definition = type("Registration", """
				package example;
				public class Registration implements me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition {
					public Object define() { return null; }
				}
				""");
		ExtensionTestUtil.maskExtensions(ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(), getTestRootDisposable());

		assertNull(new ScenarioLineMarker().getInfo(identifier(definition)));
	}

	private void listSourceOf(PsiClass definition) {
		Path root = Path.of(definition.getContainingFile().getVirtualFile().getParent().getParent().getPath());
		var source = ScenarioSource.builder().id("fixture:main").displayName("main").integrationId("fixture").directory(root).build();
		ExtensionTestUtil.maskExtensions(ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(new Listed(source)), getTestRootDisposable());
	}

	private PsiClass type(String name, String text) {
		myFixture.addFileToProject("example/" + name + ".java", text);
		return JavaPsiFacade.getInstance(getProject()).findClass("example." + name, GlobalSearchScope.projectScope(getProject()));
	}

	private static PsiIdentifier identifier(PsiClass type) {
		return PsiTreeUtil.getChildOfType(type, PsiIdentifier.class);
	}

	private record Listed(ScenarioSource source) implements BuildIntegration {
		@Override
		public @NotNull String getId() {
			return "fixture";
		}

		@Override
		public @NotNull SourceListing discover(@NotNull Project project) {
			return SourceListing.builder().sources(List.of(source)).status(SourceListingStatus.READY).message("Ready").build();
		}

		@Override
		public boolean canSync(@NotNull Project project) {
			return false;
		}

		@Override
		public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void subscribe(@NotNull Project project, @NotNull Consumer<ProjectChange> listener, @NotNull Disposable owner) {}

		@Override
		public @NotNull ScenarioPreparation prepare(@NotNull Project project, @NotNull ScenarioSource selected) {
			throw new AssertionError("Creating a configuration must not prepare the project");
		}
	}
}
