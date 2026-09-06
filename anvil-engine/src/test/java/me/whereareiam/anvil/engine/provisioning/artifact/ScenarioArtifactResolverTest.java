package me.whereareiam.anvil.engine.provisioning.artifact;

import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioArtifactResolverTest {
	@TempDir
	Path temporary;

	@Test
	void resolvesDistributionsAndAssetsWithoutChangingScenarioDeclarations() throws Exception {
		Path jar = Files.createFile(temporary.resolve("test.jar"));
		ScenarioArtifactResolver resolver = new ScenarioArtifactResolver(Map.of("test", jar), name -> jar);
		WorkspacePlan workspace = WorkspacePlan.builder().asset(WorkspaceAsset.builder()
				.source(AssetSource.artifact("test")).target(Path.of("plugins/test.jar")).build()).build();
		MinecraftServer server = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.artifact("test")).workspace(workspace).build();
		AnvilScenario scenario = AnvilScenario.builder().name("test").entrypoint("server").server(server).build();

		MinecraftServer resolved = resolver.resolve(scenario).getServers().getFirst();

		assertEquals(jar, resolved.getDistribution().getLocalJar());
		assertEquals(jar, resolved.getWorkspace().getAssets().getFirst().getSource().getPath());
		assertTrue(server.getDistribution().isArtifact());
		assertTrue(workspace.getAssets().getFirst().getSource().isArtifact());
		WorkspacePlan installed = resolver.installAgent(resolved.getWorkspace(), PlatformAgentDescriptor.builder()
				.entrypointClassName("test.Agent").build());
		assertEquals(2, installed.getAssets().size());
		assertEquals(1, resolved.getWorkspace().getAssets().size());
	}

	@Test
	void rejectsAnUnresolvedAssetBeforeAnyAgentLookup() {
		ScenarioArtifactResolver resolver = new ScenarioArtifactResolver(Map.of(), name -> {
			throw new AssertionError("Agent lookup must happen after artifact validation");
		});
		MinecraftServer server = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.workspace(WorkspacePlan.builder().asset(WorkspaceAsset.builder()
						.source(AssetSource.artifact("missing")).target(Path.of("plugins/test.jar")).build()).build())
				.build();
		AnvilException failure = assertThrows(AnvilException.class,
				() -> resolver.resolve(AnvilScenario.builder().name("test").entrypoint("server").server(server).build()));
		assertTrue(failure.getMessage().contains("No artifact named 'missing'"));
	}
}
