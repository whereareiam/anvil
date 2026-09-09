package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Resolved scenario declarations and preparation inputs keyed by process name.
 * The plan contains values only and owns no running resources.
 */
@Value
@Builder
public class PlatformPlan {
	@NotNull AnvilScenario scenario;
	@NotNull
	@Singular
	Map<String, ProcessPlan> processes;
}
