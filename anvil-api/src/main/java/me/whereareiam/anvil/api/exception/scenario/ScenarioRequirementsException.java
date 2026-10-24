package me.whereareiam.anvil.api.exception.scenario;

import lombok.Getter;
import me.whereareiam.anvil.api.exception.AnvilException;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.List;

/**
 * Reports that the workbench chosen for a scenario cannot meet what the scenario requires. Nothing of the scenario
 * was prepared or started; another workbench, or this one with a different policy, may run it.
 */
@Getter
public class ScenarioRequirementsException extends AnvilException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Name of the scenario that was refused.
	 */
	private final @NotNull String scenarioName;

	/**
	 * Name of the workbench that refused it.
	 */
	private final @NotNull String workbenchName;

	/**
	 * Every requirement the workbench does not meet, one sentence each.
	 */
	private final @NotNull List<String> unmet;

	/**
	 * Creates a refusal listing every unmet requirement.
	 *
	 * @param scenarioName refused scenario
	 * @param workbenchName refusing workbench
	 * @param unmet unmet requirements, at least one
	 */
	public ScenarioRequirementsException(
			@NotNull String scenarioName,
			@NotNull String workbenchName,
			@NotNull List<String> unmet
	) {
		super("Workbench '" + workbenchName + "' cannot run scenario '" + scenarioName + "': " + String.join("; ", unmet));
		this.scenarioName = scenarioName;
		this.workbenchName = workbenchName;
		this.unmet = List.copyOf(unmet);
	}
}
