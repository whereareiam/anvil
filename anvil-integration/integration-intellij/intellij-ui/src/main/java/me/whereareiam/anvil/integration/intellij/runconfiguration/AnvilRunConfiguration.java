package me.whereareiam.anvil.integration.intellij.runconfiguration;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Persists a detected Anvil project, stable scenario identity, and optional component target.
 */
@Getter
@Setter
public final class AnvilRunConfiguration extends RunConfigurationBase<RunConfigurationOptions> {
	private @NotNull String sourceId = "";
	private @NotNull String definition = "";
	private @NotNull String scenario = "";
	private @NotNull String processId = "";

	AnvilRunConfiguration(
			@NotNull Project project,
			@NotNull ConfigurationFactory factory,
			@NotNull String name
	) {
		super(project, factory, name);
	}

	@Override
	public @NotNull SettingsEditor<AnvilRunConfiguration> getConfigurationEditor() {
		return new RunConfigurationEditor(getProject());
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationError {
		getProject().getService(RunConfigurationService.class).validate(this);
	}

	@Override
	public @NotNull RunProfileState getState(
			@NotNull Executor executor,
			@NotNull ExecutionEnvironment environment
	) {
		return (selectedExecutor, runner) -> {
			var run = getProject().getService(RunConfigurationService.class).launch(this);
			// The Run window owns this console: it disposes it when its tab closes. The presentation prints
			// output itself, so the console is not attached to the handler, which would print it again.
			var presentation = new ConsolePresentation(getProject(), run.getLog(), run::stop, getProject());

			return new DefaultExecutionResult(presentation.getConsole(), presentation.getProcessHandler());
		};
	}

	@Override
	public void readExternal(@NotNull Element element) {
		super.readExternal(element);

		sourceId = element.getAttributeValue("sourceId", sourceId);
		definition = element.getAttributeValue("definition", definition);
		scenario = element.getAttributeValue("scenario", scenario);
		processId = element.getAttributeValue("processId", "");
	}

	@Override
	public void writeExternal(@NotNull Element element) {
		super.writeExternal(element);

		element.setAttribute("sourceId", sourceId);
		element.setAttribute("definition", definition);
		element.setAttribute("scenario", scenario);
		if (processId.isBlank()) {
			element.removeAttribute("processId");
			return;
		}

		element.setAttribute("processId", processId);
	}
}
