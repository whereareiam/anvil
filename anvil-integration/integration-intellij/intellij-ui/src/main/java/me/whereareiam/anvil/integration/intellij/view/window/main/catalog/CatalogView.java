package me.whereareiam.anvil.integration.intellij.view.window.main.catalog;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.details.DefinitionDetailsPanel;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Catalog screen operations available to the catalog controllers.
 * The screen owns layout and Swing state; controllers decide what it shows.
 */
interface CatalogView {
	@Nullable ScenarioDescriptor selectedScenario();

	@Nullable ProcessDefinition selectedProcess();

	void setAutoExpand(boolean expanded);

	void renderSources(
			@NotNull List<ScenarioSource> available,
			@Nullable ScenarioSource selected,
			boolean enabled
	);

	void applyCatalog(@NotNull List<ScenarioDescriptor> scenarios);

	void showExecution(
			@Nullable ScenarioDescriptor scenario,
			@Nullable SessionSnapshot snapshot,
			@Nullable EnvironmentState state
	);

	@NotNull DefinitionDetailsPanel details();

	void updateActions();

	void showMessage(
			@NotNull String title,
			@Nullable String body,
			@Nullable String action,
			@Nullable Runnable callback
	);

	void showSyncFailure(@NotNull String details, @NotNull Runnable inspect, @NotNull Runnable retry);

	void showCatalog();
}
