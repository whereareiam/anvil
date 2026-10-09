package me.whereareiam.anvil.example.patience.scenario;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.Platforms;

import java.nio.file.Path;

/** Shared scenario construction helpers for the proof-of-patience definitions. */
public final class ProofOfPatienceScenarios {
	private ProofOfPatienceScenarios() {
	}

	static final String PAPER_1_21_11 = "proof-of-patience-paper-1.21.11";
	static final String PAPER_26_1_2 = "proof-of-patience-paper-26.1.2";

	static AnvilScenario paper(String version, String build, String name) {
		return paper(version, build, name, false);
	}

	static AnvilScenario paper(String version, String build, String name, boolean manual) {
		MinecraftServer authentication = MinecraftServer.builder()
				.name("authentication")
				.metadata(PresentationMetadata.builder()
						.displayName("Authentication server")
						.description("Runs the plugin under test and handles /auth, reconnect rejection "
								+ "and authenticated player identity.")
						.build())
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote(version, build))
				.workspace(WorkspacePlan.builder()
						.asset(WorkspaceAsset.builder()
								.group("proof-of-patience")
								.source(AssetSource.artifact("plugin-under-test"))
								.target(Path.of("plugins", "proof-of-patience.jar"))
								.build())
						.build())
				.memoryMegabytes(768)
				.build();

		return AnvilScenario.builder()
				.name(name)
				.metadata(PresentationMetadata.builder()
						.displayName("Reconnect authentication · Paper " + version + (manual ? " (manual)" : ""))
						.description("Installs Proof of Patience to exercise the /auth reconnect challenge: "
								+ "the next two connections are rejected, and the third authenticates the player "
								+ "with the configured UUID. The automated journey checks the kicks and identity change.")
						.category("Proof of Patience")
						.tag("authentication").tag("reconnect")
						.build())
				.entrypoint(authentication.getName())
				.server(authentication)
				.manual(manual)
				.build();
	}

}
