package me.whereareiam.anvil.testkit.tests.server.scenario;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal scenario factory used to verify every native platform and protocol combination
 * supported by Anvil.
 */
public final class CompatibilityScenarioFactory {
	private static final String VELOCITY_VERSION = "3.5.1";
	private static final String VELOCITY_BUILD = "615";
	private static final String BUNGEE_BUILD = "2085";

	/**
	 * Returns pinned direct and routed environments used by compatibility tests.
	 *
	 * @return all supported compatibility scenarios
	 */
	public static List<AnvilScenario> scenarios() {
		List<AnvilScenario> scenarios = new ArrayList<>();
		registerVersion("1.21.11", "132",
				"6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd",
				new Paper12111SystemScenario(), scenarios);
		registerVersion("26.1.2", "74",
				"95f871fd6d055ba10b5a058768ddad43b0be0286480c8eba435c835c95d5f19c",
				new Paper2612SystemScenario(), scenarios);
		return List.copyOf(scenarios);
	}

	private static void registerVersion(
			String version,
			String paperBuild,
			String spigotSha256,
			AnvilScenarioDefinition paperScenario,
			List<AnvilScenario> matrix
	) {
		Distribution paper = Distribution.remote(version, paperBuild);
		Distribution spigot = Distribution.pinned(version, spigotSha256);
		register(matrix, paperScenario.define());
		register(matrix, directScenario(
				"spigot-" + version,
				Platforms.SPIGOT,
				spigot
		));
		register(matrix, proxyScenario(
				"velocity-paper-" + version,
				Platforms.VELOCITY,
				Platforms.PAPER,
				paper
		));
		register(matrix, proxyScenario(
				"velocity-spigot-" + version,
				Platforms.VELOCITY,
				Platforms.SPIGOT,
				spigot
		));
		register(matrix, proxyScenario(
				"bungee-paper-" + version,
				Platforms.BUNGEECORD,
				Platforms.PAPER,
				paper
		));
		register(matrix, proxyScenario(
				"bungee-spigot-" + version,
				Platforms.BUNGEECORD,
				Platforms.SPIGOT,
				spigot
		));
	}

	private static void register(List<AnvilScenario> scenarios, AnvilScenario scenario) {
		scenarios.add(scenario);
	}

	static AnvilScenario paperScenario(String version, String build) {
		return directScenario("paper-" + version, Platforms.PAPER, Distribution.remote(version, build));
	}

	private static AnvilScenario directScenario(String name, String platform, Distribution distribution) {
		MinecraftServer server = server("server", platform, distribution);
		return AnvilScenario.builder()
				.name(name)
				.metadata(PresentationMetadata.builder()
						.displayName("Direct login · " + platformLabel(platform) + " " + distribution.getVersion())
						.description("Single-server fixture for checking native login, plugin commands, server-side player observation "
								+ "and agent extension calls. Restart tests reuse it to verify reconnection with the same player "
								+ "identity, address and workspace.")
						.category("Native compatibility").tag("login").tag("restart").build())
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
				.metadata(PresentationMetadata.builder()
						.displayName("Backend switching · " + platformLabel(proxyPlatform) + " / "
								+ platformLabel(serverPlatform) + " " + distribution.getVersion())
						.description("Two backends let compatibility tests join through " + platformLabel(proxyPlatform)
								+ ", move from Lobby to Game, and verify that plugin commands reach the new backend. "
								+ "Restart tests reuse this environment to check reconnection, preserved player identity "
								+ "and agent extension calls after restarting the proxy or Lobby.")
						.category("Native compatibility").tag("routing").tag("restart").build())
				.entrypoint(proxy.getName())
				.server(lobby)
				.server(game)
				.proxy(proxy)
				.build();
	}

	private static MinecraftServer server(String name, String platform, Distribution distribution) {
		Path fixture = FixtureArtifacts.serverPlugin();
		return MinecraftServer.builder()
				.name(name)
				.metadata(serverMetadata(name))
				.platform(platform)
				.distribution(distribution)
				.workspace(WorkspacePlan.builder()
						.asset(WorkspaceAsset.builder()
								.group("fixture")
								.source(AssetSource.path(fixture))
								.target(Path.of("plugins", fixture.getFileName().toString()))
								.build())
						.asset(WorkspaceAsset.builder()
								.group("fixture-agent-extension")
								.source(AssetSource.path(fixture))
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
				.metadata(PresentationMetadata.builder()
						.displayName("Proxy")
						.description("Accepts the player's connection and routes it to Lobby or Game. "
								+ "Restart tests verify that the player can reconnect through the replacement proxy.")
						.build())
				.platform(platform)
				.distribution(distribution)
				.server(defaultServer)
				.server(otherServer)
				.defaultServer(defaultServer)
				.build();
	}

	private static PresentationMetadata serverMetadata(String name) {
		return switch (name) {
			case "lobby" -> PresentationMetadata.builder()
					.displayName("Lobby")
					.description("Default join destination. Tests verify initial login here and reconnect after "
							+ "restarting this backend while the same proxy stays running.")
					.build();
			case "game" -> PresentationMetadata.builder()
					.displayName("Game")
					.description("Transfer destination. Tests move the connected player here from Lobby and verify "
							+ "that fixture commands still reach the backend.")
					.build();
			default -> PresentationMetadata.builder()
					.displayName("Test server")
					.description("Direct connection target with the fixture plugin and agent extension installed. "
							+ "Used for login, commands, player observation and reconnection after restart.")
					.build();
		};
	}

	private static String platformLabel(String platform) {
		return switch (platform) {
			case Platforms.PAPER -> "Paper";
			case Platforms.SPIGOT -> "Spigot";
			case Platforms.VELOCITY -> "Velocity";
			case Platforms.BUNGEECORD -> "BungeeCord";
			default -> platform;
		};
	}
}
