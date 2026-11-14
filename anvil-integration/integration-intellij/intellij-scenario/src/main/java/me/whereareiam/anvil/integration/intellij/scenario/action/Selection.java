package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

import lombok.Value;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The scenario, process, and session an action applies to, read from the event's data context.
 */
@Value
class Selection {
	@NotNull Project project;
	@NotNull ScenarioSource source;
	@NotNull ScenarioDescriptor scenario;
	/**
	 * Selected process name, or null when the scenario itself is selected.
	 */
	@Nullable String process;
	/**
	 * Session the invoking view controls, if any.
	 */
	@Nullable EnvironmentSession session;

	/**
	 * Reads the selection, or returns null when the view offers no scenario.
	 */
	static @Nullable Selection of(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		ScenarioSource source = event.getData(ScenarioSource.DATA_KEY);
		ScenarioDescriptor scenario = event.getData(ScenarioDataKeys.SCENARIO);
		if (project == null || source == null || scenario == null) return null;

		return new Selection(project, source, scenario, event.getData(ScenarioDataKeys.PROCESS), event.getData(ScenarioDataKeys.SESSION));
	}

	/**
	 * Returns why nothing can start until the project has settled, or null when it has.
	 */
	@Nullable String waiting() {
		if (SourceDiscovery.getInstance(project).snapshot().getState() == SourceDiscovery.State.SYNCING)
			return "Wait until the project sync has finished";
		if (ScenarioCatalog.getInstance(project).snapshot().getState() == ScenarioCatalog.State.LOADING && session == null)
			return "Wait until the scenarios have loaded";

		return null;
	}

	/**
	 * Returns the session when it runs the selected scenario.
	 */
	@Nullable EnvironmentSession runningSession() {
		return session != null && session.isActive() && session.runs(source, scenario) ? session : null;
	}

	/**
	 * Returns the declaration of the selected process, or null for the scenario or a process it does not declare.
	 */
	@Nullable ProcessDefinition definition() {
		if (process == null) return null;

		return scenario.getProcesses().stream().filter(candidate -> candidate.getName().equals(process)).findFirst().orElse(null);
	}

	/**
	 * Names the selected process by its role, such as "server" or "proxy".
	 */
	@NotNull String role() {
		ProcessDefinition definition = definition();
		return definition != null && definition.getRole() == ProcessRole.PROXY ? "Proxy" : "Server";
	}
}
