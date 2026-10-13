package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.List;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Saves, validates, and launches Anvil run configurations of one project.
 * <p>
 * Launching resolves the saved source and definition and runs them through {@link EnvironmentLifecycle}, so a run
 * configuration follows the same one-environment rule as the tool window.
 */
@Service(Service.Level.PROJECT)
@RequiredArgsConstructor
public final class RunConfigurationService {
	private final @NotNull Project project;

	/**
	 * Returns the run configuration service of the given project.
	 */
	public static @NotNull RunConfigurationService getInstance(@NotNull Project project) {
		return project.getService(RunConfigurationService.class);
	}

	/**
	 * Saves, or reuses, the configuration of exactly this scenario or process and selects it without running it.
	 */
	public @NotNull RunnerAndConfigurationSettings save(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@Nullable ProcessDefinition process
	) {
		RunManager manager = RunManager.getInstance(project);
		String processId = process == null ? "" : process.getName();
		var settings = find(manager, source, scenario.getDefinition(), processId);
		if (settings == null) {
			var type = AnvilConfigurationType.getInstance();
			settings = manager.createConfiguration(manager.suggestUniqueName(displayName(scenario, process), type), type.factory());
		}

		var configuration = (AnvilRunConfiguration) settings.getConfiguration();
		configuration.setSourceId(source.getId());
		configuration.setDefinition(scenario.getDefinition());
		configuration.setProcessId(processId);
		manager.addConfiguration(settings);
		manager.setSelectedConfiguration(settings);

		return settings;
	}

	/**
	 * Checks the saved identities against the imported sources without preparing anything.
	 */
	public void validate(@NotNull AnvilRunConfiguration configuration) throws RuntimeConfigurationError {
		try {
			resolveSource(configuration);
		} catch (IllegalStateException failure) {
			throw new RuntimeConfigurationError(failure.getMessage());
		}
	}

	/**
	 * Resolves the configuration's source and scenario and runs them.
	 *
	 * @throws ExecutionException if the source is unavailable or another environment is running
	 */
	public @NotNull EnvironmentSession launch(@NotNull AnvilRunConfiguration configuration) throws ExecutionException {
		try {
			ScenarioSource source = resolveSource(configuration);
			String process = configuration.getProcessId().isBlank() ? null : configuration.getProcessId();
			return EnvironmentLifecycle.getInstance(project).run(source, resolveScenario(source, configuration), process);
		} catch (IllegalStateException failure) {
			throw new ExecutionException(failure.getMessage(), failure);
		}
	}

	/**
	 * Lists the sources an editor can offer, with the one a saved ID selects and a hint for the user.
	 */
	@NotNull SourceChoice sourceChoice(@NotNull String savedId) {
		var sources = project.getService(BuildIntegrations.class);
		var listing = sources.discover();
		ScenarioSource selected;
		try {
			selected = sources.resolve(savedId);
		} catch (IllegalStateException unavailable) {
			selected = null;
		}

		return new SourceChoice(listing.getSources(), selected, hint(listing, selected, savedId));
	}

	private @NotNull ScenarioSource resolveSource(@NotNull AnvilRunConfiguration configuration) {
		var source = project.getService(BuildIntegrations.class).resolve(configuration.getSourceId());
		if (configuration.getDefinition().isBlank())
			throw new IllegalStateException("Enter the scenario's definition class, or save the configuration from the Anvil tool window.");

		return source;
	}

	private @NotNull ScenarioDescriptor resolveScenario(@NotNull ScenarioSource source, @NotNull AnvilRunConfiguration configuration) {
		var catalog = ScenarioCatalog.getInstance(project).snapshot();
		ScenarioDescriptor loaded = catalog.isFor(source) ? catalog.find(configuration.getDefinition()) : null;
		if (loaded != null) return loaded;

		// The runner selects by definition; the descriptor is completed once the launch has loaded the scenarios.
		String definition = configuration.getDefinition();
		return ScenarioDescriptor.builder()
				.definition(definition)
				.name(definition.substring(definition.lastIndexOf('.') + 1))
				.displayName(configuration.getName())
				.build();
	}

	private static @Nullable RunnerAndConfigurationSettings find(
			@NotNull RunManager manager,
			@NotNull ScenarioSource source,
			@NotNull String definition,
			@NotNull String processId
	) {
		for (var settings : manager.getAllSettings()) {
			if (!(settings.getConfiguration() instanceof AnvilRunConfiguration configuration)) continue;
			if (configuration.getSourceId().equals(source.getId())
					&& configuration.getDefinition().equals(definition)
					&& configuration.getProcessId().equals(processId)) return settings;
		}

		return null;
	}

	private static @NotNull String displayName(@NotNull ScenarioDescriptor scenario, @Nullable ProcessDefinition process) {
		if (process == null) return scenario.getDisplayName();
		if (!process.getDisplayName().equals(scenario.getDisplayName()))
			return scenario.getDisplayName() + " · " + process.getDisplayName();

		return scenario.getDisplayName() + " · " + (process.getRole() == ProcessRole.PROXY ? "Proxy" : "Server");
	}

	private static @NotNull String hint(@NotNull SourceListing listing, @Nullable ScenarioSource selected, @NotNull String savedId) {
		if (listing.getSources().isEmpty()) return listing.getMessage();
		if (selected == null && !savedId.isBlank()) return "The saved scenario source is unavailable. Choose its replacement.";
		if (listing.getSources().size() == 1) return "Detected in the open project.";

		return "Choose the source containing this scenario.";
	}

	/**
	 * Sources an editor can offer.
	 */
	record SourceChoice(@NotNull List<ScenarioSource> available, @Nullable ScenarioSource selected, @NotNull String hint) {}
}
