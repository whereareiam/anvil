package me.whereareiam.anvil.integration.intellij.scenario.editor.inspection;

import me.whereareiam.anvil.integration.intellij.ScenarioApiFixture;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;

public class UnknownPlatformVersionInspectionPlatformTest extends ScenarioPlatformTestCase {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ScenarioApiFixture.add(myFixture);
		myFixture.addFileToProject("me/whereareiam/anvil/platform/paper/paper-versions.toml", """
				known = [
					"1.21.10", "1.21.11",
				]

				[verified]
				"26.1.2" = [25]
				""");
		myFixture.enableInspections(new UnknownPlatformVersionInspection());
	}

	public void testReportsVersionsThePlatformDoesNotKnow() {
		myFixture.configureByText("Versions.java", """
				import me.whereareiam.anvil.api.model.process.*;
				class Versions {
					static final String PAPER = "paper";
					void define() {
						MinecraftServer.builder().platform(PAPER).distribution(Distribution.remote(<warning descr="Anvil does not know paper 1.21.12; it runs untested, and the strict support policy refuses it">"1.21.12"</warning>, "1")).build();
						MinecraftServer.builder().distribution(Distribution.pinned(<warning descr="Anvil does not know paper 1.2; it runs untested, and the strict support policy refuses it">"1.2"</warning>, "abc")).platform("paper").build();
						MinecraftServer.builder().platform("paper").distribution(Distribution.remote("1.21.11", "132")).build();
						MinecraftServer.builder().platform("paper").distribution(Distribution.remote("26.1.2", "1")).build();
					}
				}
				""");

		myFixture.checkHighlighting();
	}

	public void testSkipsPlatformsWithoutDataAndComputedValues() {
		myFixture.configureByText("Skipped.java", """
				import me.whereareiam.anvil.api.model.process.*;
				class Skipped {
					void define(String version, String platform) {
						MinecraftServer.builder().platform("spigot").distribution(Distribution.remote("9.9", "1")).build();
						MinecraftServer.builder().platform("paper").distribution(Distribution.remote(version, "1")).build();
						MinecraftServer.builder().platform(platform).distribution(Distribution.remote("9.9", "1")).build();
						Distribution.remote("9.9", "1");
					}
				}
				""");

		myFixture.checkHighlighting();
	}
}
