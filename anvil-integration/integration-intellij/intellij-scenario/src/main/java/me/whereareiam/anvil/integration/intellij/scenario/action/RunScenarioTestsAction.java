package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.Arrays;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.scenario.ScenarioNotifications;
import me.whereareiam.anvil.integration.intellij.scenario.editor.ScenarioDefinitions;
import me.whereareiam.anvil.integration.intellij.scenario.editor.ScenarioTests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the JUnit tests that start the selected scenario, with the IDE's test runner.
 * <p>
 * The scenario comes from the catalog selection or from the definition class at the editor location. The IDE
 * creates the test configuration from the tests found, as if they were selected together in the project view,
 * so the build integration that runs ordinary tests runs these too.
 */
public final class RunScenarioTestsAction extends AnAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		boolean available = Selection.of(event) != null || ScenarioDefinitions.containing(location(event)) != null;
		event.getPresentation().setEnabledAndVisible(available);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		Selection selection = Selection.of(event);
		PsiElement location = selection == null ? location(event) : null;
		ReadAction.nonBlocking(() -> {
					PsiClass definition = selection == null ? ScenarioDefinitions.containing(location) : definition(selection);
					return definition == null ? null : new Found(definition.getName(), ScenarioTests.using(definition));
				})
				.inSmartMode(project)
				.expireWith(project)
				.finishOnUiThread(ModalityState.nonModal(), found -> run(project, found))
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	private void run(@NotNull Project project, @Nullable Found found) {
		if (found == null) {
			ScenarioNotifications.error(project, "Scenario not found", "The scenario's definition class is not in the project's sources.");
			return;
		}
		if (found.tests().isEmpty()) {
			ScenarioNotifications.info(project, "No tests use " + found.name(),
					"Annotate a test class or method with @AnvilTest(" + found.name() + ".class) to run it here.");
			return;
		}

		RunnerAndConfigurationSettings settings = configuration(project, found.tests());
		if (settings == null) {
			ScenarioNotifications.error(project, "Cannot run tests",
					"No test runner accepts " + ScenarioTests.label(found.tests().getFirst()) + ".");
			return;
		}

		var runs = RunManager.getInstance(project);
		if (runs.findSettings(settings.getConfiguration()) == null) runs.setTemporaryConfiguration(settings);
		runs.setSelectedConfiguration(settings);
		ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
	}

	/**
	 * Returns the configuration the IDE's test producers create for the tests, or null when none applies.
	 */
	static @Nullable RunnerAndConfigurationSettings configuration(
			@NotNull Project project,
			@NotNull List<PsiModifierListOwner> tests
	) {
		PsiElement first = tests.getFirst();
		Location<?>[] locations = tests.stream().map(PsiLocation::fromPsiElement).toArray(Location[]::new);
		var data = SimpleDataContext.builder()
				.add(CommonDataKeys.PROJECT, project)
				.add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(first))
				.add(CommonDataKeys.PSI_ELEMENT, first)
				.add(LangDataKeys.PSI_ELEMENT_ARRAY, tests.toArray(PsiElement[]::new))
				.add(Location.DATA_KEY, locations[0])
				.add(Location.DATA_KEYS, tests.size() > 1 ? locations : null)
				.build();

		return ConfigurationContext.getFromContext(data, ActionPlaces.UNKNOWN).getConfiguration();
	}

	private static @Nullable PsiElement location(@NotNull AnActionEvent event) {
		Location<?> location = event.getData(Location.DATA_KEY);
		if (location != null) return location.getPsiElement();

		PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
		Editor editor = event.getData(CommonDataKeys.EDITOR);
		if (file != null && editor != null) return file.findElementAt(editor.getCaretModel().getOffset());

		return event.getData(CommonDataKeys.PSI_ELEMENT);
	}

	private static @Nullable PsiClass definition(@NotNull Selection selection) {
		PsiClass[] candidates = JavaPsiFacade.getInstance(selection.getProject())
				.findClasses(selection.getScenario().getDefinition(), GlobalSearchScope.projectScope(selection.getProject()));

		return Arrays.stream(candidates)
				.filter(candidate -> {
					var source = ScenarioDefinitions.source(candidate);
					return source != null && source.getId().equals(selection.getSource().getId());
				})
				.findFirst()
				.orElse(candidates.length == 0 ? null : candidates[0]);
	}

	private record Found(@NotNull String name, @NotNull List<PsiModifierListOwner> tests) {}
}
