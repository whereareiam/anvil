package me.whereareiam.anvil.example.patience.scenario;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Proof-of-patience environments for automated release checks and developers who still wish to
 * perform the ceremony personally.
 */
public final class ProofOfPatienceScenarios implements AnvilScenarioProvider {
	static final String PAPER_1_21_11 = "proof-of-patience-paper-1.21.11";
	static final String PAPER_26_1_2 = "proof-of-patience-paper-26.1.2";
	private static final String MANUAL_PAPER_1_21_11 = PAPER_1_21_11 + "-manual";
	private static final String MANUAL_PAPER_26_1_2 = PAPER_26_1_2 + "-manual";

	@Override
	public void register(@NotNull ScenarioRegistry registry) {
		AnvilScenario paper12111 = new Paper12111Scenario().define();
		AnvilScenario paper2612 = new Paper2612Scenario().define();
		AnvilScenario manual12111 = paper("1.21.11", "132", MANUAL_PAPER_1_21_11, true);
		AnvilScenario manual2612 = paper("26.1.2", "74", MANUAL_PAPER_26_1_2, true);

		registry.scenario(paper12111);
		registry.scenario(paper2612);
		registry.scenario(manual12111);
		registry.scenario(manual2612);
		registry.group(ScenarioGroup.builder()
				.name("proof-of-patience-release")
				.scenario(paper12111.getName())
				.scenario(paper2612.getName())
				.build());
		registry.group(ScenarioGroup.builder()
				.name("proof-of-patience-development")
				.scenario(manual12111.getName())
				.scenario(manual2612.getName())
				.build());
	}

	static AnvilScenario paper(String version, String build, String name) {
		return paper(version, build, name, false);
	}

	private static AnvilScenario paper(String version, String build, String name, boolean manual) {
		MinecraftServer authentication = MinecraftServer.builder()
				.name("authentication")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote(version, build))
				.workspace(WorkspacePlan.builder()
						.asset(WorkspaceAsset.builder()
								.group("proof-of-patience")
								.source(AssetSource.path(plugin()))
								.target(Path.of("plugins", plugin().getFileName().toString()))
								.build())
						.build())
				.memoryMegabytes(768)
				.build();

		return AnvilScenario.builder()
				.name(name)
				.entrypoint(authentication.getName())
				.server(authentication)
				.manual(manual)
				.build();
	}

	private static Path plugin() {
		String configured = System.getProperty("anvil.example.proofOfPatience.plugin");
		if (configured != null)
			return Path.of(configured).toAbsolutePath().normalize();
		String registered = System.getProperty("anvil.artifact.plugin-under-test");
		if (registered != null)
			return Path.of(registered).toAbsolutePath().normalize();

		throw new IllegalStateException("The plugin-under-test artifact must be supplied by the Anvil Gradle plugin");
	}
}
