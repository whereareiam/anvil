package me.whereareiam.anvil.integration.intellij.view.window.main.catalog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.function.BiConsumer;

import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Composes the catalog's discovery, execution, and command owners for one view lifetime.
 * Catalog changes observed by discovery refresh the execution overlay; disposal releases every owner.
 */
final class CatalogController implements Disposable {
	private final @NotNull CatalogDiscoveryController discovery;
	private final @NotNull CatalogExecutionController execution;
	private final @NotNull CatalogCommandController commands;

	CatalogController(
			@NotNull Project project,
			@NotNull CatalogView view,
			@NotNull BiConsumer<ScenarioSource, ScenarioDescriptor> starter,
			@NotNull ScenarioCatalogPanel.ProcessStarter processStarter
	) {
		var environments = project.getService(EnvironmentLifecycle.class);
		discovery = new CatalogDiscoveryController(project, view, environments, this::catalogChanged);
		execution = new CatalogExecutionController(view, discovery, environments);
		commands = new CatalogCommandController(project, view, discovery, environments, starter, processStarter);
		// Children are disposed in reverse registration order: commands, execution, then discovery.
		Disposer.register(this, discovery);
		Disposer.register(this, execution);
		Disposer.register(this, commands);
	}

	private void catalogChanged() {
		execution.update();
	}

	@NotNull CatalogDiscoveryController discovery() {
		return discovery;
	}

	@NotNull CatalogExecutionController execution() {
		return execution;
	}

	@NotNull CatalogCommandController commands() {
		return commands;
	}

	@Override
	public void dispose() {
		// Owners are registered children and are released by the disposer.
	}
}
