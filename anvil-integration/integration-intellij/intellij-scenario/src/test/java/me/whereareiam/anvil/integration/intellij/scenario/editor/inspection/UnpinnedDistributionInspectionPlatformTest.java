package me.whereareiam.anvil.integration.intellij.scenario.editor.inspection;

import me.whereareiam.anvil.integration.intellij.ScenarioApiFixture;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;

public class UnpinnedDistributionInspectionPlatformTest extends ScenarioPlatformTestCase {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ScenarioApiFixture.add(myFixture);
		myFixture.enableInspections(new UnpinnedDistributionInspection());
	}

	public void testReportsTheLatestBuildInAnAutomatedScenario() {
		myFixture.configureByText("Latest.java", """
				import me.whereareiam.anvil.api.model.process.*;
				import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
				class Latest {
					static final String LATEST = "latest";
					AnvilScenario define() {
						var a = MinecraftServer.builder().distribution(Distribution.remote("1.21.11", <warning descr="Automated runs refuse the latest build; pin a build, or mark the scenario manual">"latest"</warning>)).build();
						var b = MinecraftServer.builder().distribution(Distribution.remote("1.21.11", <warning descr="Automated runs refuse the latest build; pin a build, or mark the scenario manual">LATEST</warning>)).build();
						var c = MinecraftServer.builder().distribution(Distribution.remote("1.21.11", "132")).build();
						return AnvilScenario.builder().server(a).server(b).server(c).build();
					}
				}
				""");

		myFixture.checkHighlighting();
	}

	public void testAcceptsTheLatestBuildInAManualScenario() {
		myFixture.configureByText("Manual.java", """
				import me.whereareiam.anvil.api.model.process.*;
				import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
				class Manual {
					AnvilScenario define() {
						var server = MinecraftServer.builder().distribution(Distribution.remote("1.21.11", "latest")).build();
						return AnvilScenario.builder().server(server).manual(true).build();
					}
				}
				""");

		myFixture.checkHighlighting();
	}
}
