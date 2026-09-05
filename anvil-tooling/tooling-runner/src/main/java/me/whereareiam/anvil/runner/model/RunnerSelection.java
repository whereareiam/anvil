package me.whereareiam.anvil.runner.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Initial scenario and optional group restriction selected for an interactive session.
 */
@Value
@Builder
public class RunnerSelection {
	@NotNull AnvilScenario scenario;
	@Nullable ScenarioGroup group;
}
