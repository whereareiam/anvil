package me.whereareiam.anvil.testing.server.scenario;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal catalog used to verify every native platform and protocol combination supported by
 * Anvil.
 */
public final class CompatibilityScenarioCatalog implements AnvilScenarioProvider {
	private static final String VELOCITY_VERSION = "3.5.1";
	private static final String VELOCITY_BUILD = "615";
	private static final String BUNGEE_BUILD = "2085";

	@Override
	public void register(@NotNull ScenarioRegistry registry) {
		List<String> matrix = new ArrayList<>();
		registerVersion(registry, "1.21.11", "132",
				"6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd",
				new Paper12111SystemScenario(), matrix);
		registerVersion(registry, "26.1.2", "74",
				"95f871fd6d055ba10b5a058768ddad43b0be0286480c8eba435c835c95d5f19c",
				new Paper2612SystemScenario(), matrix);
		registry.group(ScenarioGroup.builder().name("native-compatibility-matrix").scenarios(matrix).build());
	}

	private static void registerVersion(
			ScenarioRegistry registry,
			String version,
			String paperBuild,
			String spigotSha256,
			AnvilScenarioDefinition paperScenario,
			List<String> matrix
	) {
		Distribution paper = Distribution.remote(version, paperBuild);
		Distribution spigot = Distribution.pinned(version, spigotSha256);
		register(registry, matrix, paperScenario.define());
		register(registry, matrix, directScenario(
				"spigot-" + version,
				Platforms.SPIGOT,
				spigot
		));
		register(registry, matrix, proxyScenario(
				"velocity-paper-" + version,
				Platforms.VELOCITY,
				Platforms.PAPER,
				paper
		));
		register(registry, matrix, proxyScenario(
				"velocity-spigot-" + version,
				Platforms.VELOCITY,
				Platforms.SPIGOT,
				spigot
		));
		register(registry, matrix, proxyScenario(
				"bungee-paper-" + version,
				Platforms.BUNGEECORD,
				Platforms.PAPER,
				paper
		));
		register(registry, matrix, proxyScenario(
				"bungee-spigot-" + version,
				Platforms.BUNGEECORD,
				Platforms.SPIGOT,
				spigot
		));
	}

	private static void register(ScenarioRegistry registry, List<String> matrix, AnvilScenario scenario) {
		registry.scenario(scenario);
		matrix.add(scenario.getName());
	}

	static AnvilScenario paperScenario(String version, String build) {
		return directScenario("paper-" + version, Platforms.PAPER, Distribution.remote(version, build));
	}

	private static AnvilScenario directScenario(String name, String platform, Distribution distribution) {
		MinecraftServer server = server("server", platform, distribution);
		return AnvilScenario.builder()
				.name(name)
				.entrypoint(server.getName())
				.server(server)
				.build();
	}

	private static AnvilScenario proxyScenario(
			String name,
			String proxyPlatform,
			String serverPlatform,
			Distribution distribution
	) {
		MinecraftServer lobby = server("lobby", serverPlatform, distribution);
		MinecraftServer game = server("game", serverPlatform, distribution);
		MinecraftProxy proxy = proxy("proxy", proxyPlatform, lobby.getName(), game.getName());
		return AnvilScenario.builder()
				.name(name)
				.entrypoint(proxy.getName())
				.server(lobby)
				.server(game)
				.proxy(proxy)
				.build();
	}

	private static MinecraftServer server(String name, String platform, Distribution distribution) {
		return MinecraftServer.builder()
				.name(name)
				.platform(platform)
				.distribution(distribution)
				.workspace(WorkspacePlan.builder()
						.asset(WorkspaceAsset.builder()
								.group("fixture")
								.source(AssetSource.path(fixturePlugin()))
								.target(Path.of("plugins", fixturePlugin().getFileName().toString()))
								.build())
						.asset(WorkspaceAsset.builder()
								.group("fixture-agent-extension")
								.source(AssetSource.path(fixturePlugin()))
								.target(Path.of("plugins", "anvil-agent-extensions", "fixture.jar"))
								.build())
						.build())
				.memoryMegabytes(768)
				.build();
	}

	private static MinecraftProxy proxy(String name, String platform, String defaultServer, String otherServer) {
		Distribution distribution = Platforms.VELOCITY.equals(platform)
				? Distribution.remote(VELOCITY_VERSION, VELOCITY_BUILD)
				: Distribution.remote("BungeeCord", BUNGEE_BUILD);
		return MinecraftProxy.builder()
				.name(name)
				.platform(platform)
				.distribution(distribution)
				.server(defaultServer)
				.server(otherServer)
				.defaultServer(defaultServer)
				.build();
	}

	private static Path fixturePlugin() {
		String configured = System.getProperty("anvil.testing.serverPlugin");
		if (configured == null)
			throw new IllegalStateException("The serverPlugin fixture artifact was not supplied by the test task");
		return Path.of(configured).toAbsolutePath().normalize();
	}
}
