package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolved scenario declarations and preparation inputs keyed by process name.
 * The plan contains values only and owns no running resources.
 */
@Value
@Builder(toBuilder = true)
public class PlatformPlan {
	@NotNull AnvilScenario scenario;
	@NotNull
	@Singular
	Map<String, ProcessPlan> processes;

	/**
	 * Returns the Minecraft edition of each planned process, as its platform's version data declares it.
	 *
	 * @return immutable editions keyed by process name
	 */
	public @NotNull Map<String, MinecraftEdition> editions() {
		Map<String, MinecraftEdition> editions = new LinkedHashMap<>();
		processes.forEach((name, process) -> editions.put(name, process.getEdition()));

		return Collections.unmodifiableMap(editions);
	}
}
