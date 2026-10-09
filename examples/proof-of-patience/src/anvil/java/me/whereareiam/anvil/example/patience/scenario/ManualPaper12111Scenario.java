package me.whereareiam.anvil.example.patience.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.jetbrains.annotations.NotNull;

/** Runs the proof-of-patience journey against Paper 1.21.11 as a manual scenario. */
public final class ManualPaper12111Scenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		return ProofOfPatienceScenarios.paper(
				"1.21.11",
				"132",
				ProofOfPatienceScenarios.PAPER_1_21_11 + "-manual",
				true
		);
	}
}
