package me.whereareiam.anvil.example.patience.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.jetbrains.annotations.NotNull;

/** Runs the proof-of-patience journey against Paper 26.1.2 as a manual scenario. */
public final class ManualPaper2612Scenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		return ProofOfPatienceScenarios.paper(
				"26.1.2",
				"74",
				ProofOfPatienceScenarios.PAPER_26_1_2 + "-manual",
				true
		);
	}
}
