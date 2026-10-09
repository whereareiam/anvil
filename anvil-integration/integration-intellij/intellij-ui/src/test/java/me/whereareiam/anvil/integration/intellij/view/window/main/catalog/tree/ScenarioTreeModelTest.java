package me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree;

import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTreeModelTest {
	@Test
	void searchMatchesPresentationAndProcessPlatforms() {
		ScenarioDescriptor scenario = WindowTestSupport.scenario();

		assertTrue(ScenarioTreeModel.matches(scenario, "velocity"));
		assertTrue(ScenarioTreeModel.matches(scenario, "smoke"));
		assertTrue(ScenarioTreeModel.matches(scenario, "authentication"));
	}

	@Test
	void searchIgnoresMissingMetadata() {
		ScenarioDescriptor pending = ScenarioDescriptor.builder()
				.definition("example.Provider")
				.name("plain")
				.displayName("Plain")
				.build();

		assertFalse(ScenarioTreeModel.matches(pending, "null"));
	}
}
