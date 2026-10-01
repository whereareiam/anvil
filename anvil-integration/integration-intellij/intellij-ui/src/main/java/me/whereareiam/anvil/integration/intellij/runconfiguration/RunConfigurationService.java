package me.whereareiam.anvil.integration.intellij.runconfiguration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.project.Project;

import java.util.List;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates saved IntelliJ configurations for one project without owning environment execution.
 */
@RequiredArgsConstructor
public final class RunConfigurationService {
	private final @NotNull Project project;

	/**
	 * Saves or reuses an exact scenario/process configuration and selects it without launching.
	 */
	public @NotNull RunnerAndConfigurationSettings save(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@Nullable ProcessDefinition process
	) {
		RunManager manager = RunManager.getInstance(project);
		String processId = process == null ? "" : process.getName();
		var settings = find(manager, source, scenario, processId);
		if (settings == null) {
			var type = ConfigurationTypeUtil.findConfigurationType(AnvilConfigurationType.class);
			settings = manager.createConfiguration(
					manager.suggestUniqueName(displayName(scenario, process), type),
					type.getConfigurationFactories()[0]);
		}
		var configuration = (AnvilRunConfiguration) settings.getConfiguration();
		configuration.setSourceId(source.getId());
		configuration.setDefinition(scenario.getDefinition());
		configuration.setScenario(scenario.getName());
		configuration.setProcessId(processId);
		manager.addConfiguration(settings);
		manager.setSelectedConfiguration(settings);

		return settings;
	}

	/**
	 * Validates saved identities against imported sources without preparing or starting tooling.
	 */
	public void validate(@NotNull AnvilRunConfiguration configuration) throws RuntimeConfigurationError {
		try {
			resolveSource(configuration);
		} catch (IllegalStateException failure) {
			throw new RuntimeConfigurationError(failure.getMessage());
		}
	}

	/**
	 * Resolves the current source and requests an environment through the lifecycle API.
	 */
	public @NotNull EnvironmentSession launch(@NotNull AnvilRunConfiguration configuration) throws ExecutionException {
		try {
			ScenarioSource source = resolveSource(configuration);
			ScenarioDescriptor scenario = resolveScenario(source, configuration);
			var environments = project.getService(EnvironmentLifecycle.class);
			if (configuration.getProcessId().isBlank()) return environments.start(source, scenario);

			return environments.startProcess(source, scenario, configuration.getProcessId());
		} catch (IllegalStateException failure) {
			throw new ExecutionException(failure.getMessage(), failure);
		}
	}

	/**
	 * Supplies editor choices using the same source resolution policy as validation and launch.
	 */
	@NotNull SourceSelection sourceSelection(@NotNull String savedId) {
		var sources = project.getService(BuildIntegrations.class);
		var discovery = sources.discover();
		ScenarioSource selected;
		try {
			selected = sources.resolve(savedId);
		} catch (IllegalStateException unavailable) {
			selected = null;
		}

		return new SourceSelection(discovery.getSources(), selected, sourceMessage(discovery, selected, savedId));
	}

	private @NotNull ScenarioSource resolveSource(@NotNull AnvilRunConfiguration configuration) {
		var source = project.getService(BuildIntegrations.class).resolve(configuration.getSourceId());
		if (configuration.getDefinition().isBlank() || configuration.getScenario().isBlank())
			throw new IllegalStateException(
					"Select a scenario definition and scenario in the Anvil tool window, or enter their IDs.");

		return source;
	}

	private @NotNull ScenarioDescriptor resolveScenario(
			@NotNull ScenarioSource source,
			@NotNull AnvilRunConfiguration configuration
	) {
		var catalog = project.getService(ScenarioCatalog.class).snapshot();
		if (catalog.isFor(source))
			for (ScenarioDescriptor scenario : catalog.getScenarios())
				if (scenario.getDefinition().equals(configuration.getDefinition())
						&& scenario.getName().equals(configuration.getScenario())) return scenario;

		// An unloaded catalog still permits launching the saved identity through cold discovery.
		return ScenarioDescriptor.builder()
				.definition(configuration.getDefinition())
				.name(configuration.getScenario())
				.displayName(configuration.getName())
				.build();
	}

	private @Nullable RunnerAndConfigurationSettings find(
			@NotNull RunManager manager,
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@NotNull String processId
	) {
		for (var settings : manager.getAllSettings()) {
			if (!(settings.getConfiguration() instanceof AnvilRunConfiguration configuration)) continue;
			if (configuration.getSourceId().equals(source.getId())
					&& configuration.getDefinition().equals(scenario.getDefinition())
					&& configuration.getScenario().equals(scenario.getName())
					&& configuration.getProcessId().equals(processId)) return settings;
		}

		return null;
	}

	private @NotNull String displayName(
			@NotNull ScenarioDescriptor scenario,
			@Nullable ProcessDefinition process
	) {
		if (process == null) return scenario.getDisplayName();
		if (!process.getDisplayName().equals(scenario.getDisplayName()))
			return scenario.getDisplayName() + " · " + process.getDisplayName();

		return scenario.getDisplayName() + " · " + (process.getRole() == ProcessRole.PROXY ? "Proxy" : "Server");
	}

	private @NotNull String sourceMessage(
			@NotNull SourceListing discovery,
			@Nullable ScenarioSource selected,
			@NotNull String savedId
	) {
		if (discovery.getSources().isEmpty()) return discovery.getMessage();
		if (selected == null && !savedId.isBlank())
			return "The saved scenario source is unavailable. Choose its replacement.";
		if (discovery.getSources().size() == 1) return "Detected in the open project.";

		return "Choose the source containing this scenario.";
	}

	record SourceSelection(
			@NotNull List<ScenarioSource> available,
			@Nullable ScenarioSource selected,
			@NotNull String message
	) {
	}
}
