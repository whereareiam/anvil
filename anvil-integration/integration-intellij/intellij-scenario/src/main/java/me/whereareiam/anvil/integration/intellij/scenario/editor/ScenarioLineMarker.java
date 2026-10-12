package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;

import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.ScenarioConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Puts a Run icon in the gutter beside every scenario definition class of an imported scenario source.
 * <p>
 * The icon offers the IDE's executor actions, which create the run configuration through
 * {@code ScenarioConfigurationProducer}, so running from the gutter, the class's context menu, and the run
 * configuration selector are the same operation. It also offers to run the JUnit tests that use the scenario.
 */
public final class ScenarioLineMarker extends RunLineMarkerContributor {
	@Override
	public @Nullable Info getInfo(@NotNull PsiElement element) {
		PsiClass definition = ScenarioDefinitions.atIdentifier(element);
		if (definition == null || ScenarioDefinitions.source(definition) == null) return null;

		AnAction[] actions = ArrayUtil.append(ExecutorAction.getActions(0), ActionManager.getInstance().getAction("Anvil.RunScenarioTests"));
		return new Info(AllIcons.RunConfigurations.TestState.Run, actions,
				ignored -> "Run scenario " + definition.getName());
	}
}
