package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.integration.intellij.scenario.console.ConsolePresentation;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * A saved run of one scenario, or of one of its processes, identified by its source and definition class.
 * <p>
 * Running it starts the environment through the same lifecycle as the Anvil tool window, so the environment
 * also appears there; the Run tab shows its console. Processes cannot be debugged, so Debug is not offered.
 */
@Getter
@Setter
public final class AnvilRunConfiguration extends RunConfigurationBase<RunConfigurationOptions>
		implements RunConfigurationWithSuppressedDefaultDebugAction {
	private @NotNull String sourceId = "";
	private @NotNull String definition = "";
	private @NotNull String processId = "";

	AnvilRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
		super(project, factory, name);
	}

	@Override
	public @NotNull SettingsEditor<AnvilRunConfiguration> getConfigurationEditor() {
		return new RunConfigurationEditor(getProject());
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationError {
		RunConfigurationService.getInstance(getProject()).validate(this);
	}

	@Override
	public @NotNull RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
		return (selectedExecutor, runner) -> {
			var session = RunConfigurationService.getInstance(getProject()).launch(this);
			// The Run window owns this console and disposes it with its tab. The presentation prints output itself,
			// so the console is not attached to the handler, which would print it again.
			var presentation = new ConsolePresentation(getProject(), session.getLog(), session::stop, getProject());

			return new DefaultExecutionResult(presentation.getConsole(), presentation.getProcessHandler());
		};
	}

	@Override
	public void readExternal(@NotNull Element element) {
		super.readExternal(element);
		sourceId = element.getAttributeValue("sourceId", "");
		definition = element.getAttributeValue("definition", "");
		processId = element.getAttributeValue("processId", "");
	}

	@Override
	public void writeExternal(@NotNull Element element) {
		super.writeExternal(element);
		element.setAttribute("sourceId", sourceId);
		element.setAttribute("definition", definition);
		if (processId.isBlank()) {
			element.removeAttribute("processId");
			return;
		}

		element.setAttribute("processId", processId);
	}
}
