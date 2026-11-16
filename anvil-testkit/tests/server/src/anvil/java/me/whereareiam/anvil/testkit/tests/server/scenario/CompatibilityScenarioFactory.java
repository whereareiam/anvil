package me.whereareiam.anvil.testkit.tests.server.scenario;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal scenario factory defining Anvil's live matrix from {@code live-matrix.toml}: every platform, Minecraft
 * version and Java combination these scenarios run is what the build writes into the platforms' and protocol
 * libraries' version data as verified, and each process requests its row's Java so that it runs exactly that. Every
 * release runs direct on Paper, where the release suite drives every player capability; the Spigot releases also run
 * behind both proxies, beside the Paper server of their version, and NeoForge runs direct.
 */
public final class CompatibilityScenarioFactory {
	private static final LiveMatrix MATRIX = LiveMatrix.read();

	private CompatibilityScenarioFactory() {
	}

	/**
	 * Returns every scenario of the live matrix.
	 *
	 * @return all scenarios whose combinations the version data calls verified
	 */
	public static @NotNull List<AnvilScenario> scenarios() {
		List<AnvilScenario> scenarios = new ArrayList<>(releaseScenarios());
		scenarios.addAll(platformScenarios());
		scenarios.addAll(neoForgeScenarios());
		return List.copyOf(scenarios);
	}

	/**
	 * Returns the Minecraft versions of the releases, one per MCProtocolLib release key, oldest first.
	 *
	 * @return release versions
	 */
	public static @NotNull List<MinecraftVersion> releases() {
		return MATRIX.getReleases().stream().map(LiveMatrix.Release::minecraft).toList();
	}

	/**
	 * Returns the direct Paper scenario of every release, oldest first, on the Java that planning selects by default.
	 *
	 * @return one direct Paper scenario per release
	 */
	public static @NotNull List<AnvilScenario> releaseScenarios() {
		return MATRIX.getReleases().stream().map(CompatibilityScenarioFactory::paperScenario).toList();
	}

	/**
	 * Returns the direct Paper scenario of one release, whose server loads the fixture plugin.
	 *
	 * @param version Minecraft version of the release
	 * @return the release's scenario
	 * @throws IllegalArgumentException when the live matrix has no such release
	 */
	public static @NotNull AnvilScenario releaseScenario(@NotNull MinecraftVersion version) {
		LiveMatrix.Release release = MATRIX.release(version)
				.orElseThrow(() -> new IllegalArgumentException("The live matrix has no release " + version
						+ "; it runs " + releases()));

		return paperScenario(release);
	}

	/**
	 * Returns the scenarios of the platform releases: a direct Spigot server, and Paper and Spigot servers behind
	 * Velocity and BungeeCord.
	 *
	 * @return the platform scenarios
	 */
	public static @NotNull List<AnvilScenario> platformScenarios() {
		List<AnvilScenario> scenarios = new ArrayList<>();
		for (LiveMatrix.SpigotRelease spigot : MATRIX.getSpigot()) {
			scenarios.add(directScenario("spigot-" + spigot.minecraft(), Platforms.SPIGOT, spigot(spigot), spigot.java()));
			for (String proxy : List.of(Platforms.VELOCITY, Platforms.BUNGEECORD))
				for (String server : List.of(Platforms.PAPER, Platforms.SPIGOT))
					scenarios.add(proxyScenario(proxy, server, spigot));
		}

		return List.copyOf(scenarios);
	}

	/**
	 * Returns the scenario of one platform release with two servers of one platform behind a proxy.
	 *
	 * @param proxyPlatform proxy platform, {@link Platforms#VELOCITY} or {@link Platforms#BUNGEECORD}
	 * @param serverPlatform server platform, {@link Platforms#PAPER} or {@link Platforms#SPIGOT}
	 * @param version Minecraft version of a Spigot release
	 * @return the routed scenario
	 * @throws IllegalArgumentException when the live matrix has no such Spigot release
	 */
	public static @NotNull AnvilScenario proxyScenario(
			@NotNull String proxyPlatform,
			@NotNull String serverPlatform,
			@NotNull MinecraftVersion version
	) {
		LiveMatrix.SpigotRelease spigot = MATRIX.getSpigot().stream()
				.filter(candidate -> candidate.minecraft().equals(version))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("The live matrix runs no platforms on " + version));

		return proxyScenario(proxyPlatform, serverPlatform, spigot);
	}

	/**
	 * Returns the direct NeoForge scenario of each Minecraft version NeoForge is verified on. NeoForge loads no Bukkit
	 * plugin, so these servers carry no fixture and their test uses vanilla commands only.
	 *
	 * @return one direct NeoForge scenario per verified Minecraft version
	 */
	public static @NotNull List<AnvilScenario> neoForgeScenarios() {
		return MATRIX.getNeoForge().stream().map(CompatibilityScenarioFactory::neoForgeScenario).toList();
	}

	private static AnvilScenario paperScenario(LiveMatrix.Release release) {
		return directScenario("paper-" + release.minecraft(), Platforms.PAPER, paper(release), release.java());
	}

	private static Distribution paper(LiveMatrix.Release release) {
		return Distribution.remote(release.minecraft().toString(), release.build());
	}

	private static Distribution spigot(LiveMatrix.SpigotRelease spigot) {
		return Distribution.pinned(spigot.minecraft().toString(), spigot.sha256());
	}

	private static AnvilScenario proxyScenario(
			String proxyPlatform,
			String serverPlatform,
			LiveMatrix.SpigotRelease spigot
	) {
		boolean onSpigot = serverPlatform.equals(Platforms.SPIGOT);
		Distribution distribution = onSpigot ? spigot(spigot) : paper(spigot.paper());
		int java = onSpigot ? spigot.java() : spigot.paper().java();
		String name = (proxyPlatform.equals(Platforms.VELOCITY) ? "velocity-" : "bungee-") + serverPlatform + "-"
				+ spigot.minecraft();

		return proxyScenario(name, proxyPlatform, serverPlatform, distribution, java);
	}

	private static AnvilScenario neoForgeScenario(LiveMatrix.NeoForgeRelease release) {
		String version = release.minecraft().toString();
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.metadata(serverMetadata("server"))
				.platform(Platforms.NEOFORGE)
				.distribution(Distribution.remote(version, release.release()))
				.javaSelection(java(release.java()))
				.memoryMegabytes(1536)
				.build();
		return AnvilScenario.builder()
				.name("neoforge-" + version)
				.metadata(PresentationMetadata.builder()
						.displayName("Direct login · NeoForge " + version)
						.description("Single NeoForge server without fixture plugins for checking native login, server-side "
								+ "player observation and console commands through the NeoForge agent.")
						.category("Native compatibility").tag("login").build())
				.entrypoint(server.getName())
				.server(server)
				.build();
	}

	private static AnvilScenario directScenario(String name, String platform, Distribution distribution, int java) {
		MinecraftServer server = server("server", platform, distribution, java);
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
			Distribution distribution,
			int java
	) {
		MinecraftServer lobby = server("lobby", serverPlatform, distribution, java);
		MinecraftServer game = server("game", serverPlatform, distribution, java);
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

	private static MinecraftServer server(String name, String platform, Distribution distribution, int java) {
		Path fixture = FixtureArtifacts.serverPlugin();
		return MinecraftServer.builder()
				.name(name)
				.metadata(serverMetadata(name))
				.platform(platform)
				.distribution(distribution)
				.javaSelection(java(java))
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
		LiveMatrix.Proxies proxies = MATRIX.getProxies();
		boolean velocity = Platforms.VELOCITY.equals(platform);
		Distribution distribution = velocity
				? Distribution.remote(proxies.velocityVersion(), proxies.velocityBuild())
				: Distribution.remote("BungeeCord", proxies.bungeeCordBuild());
		JavaSelection java = velocity ? java(proxies.velocityJava()) : JavaSelection.builder().build();
		return MinecraftProxy.builder()
				.name(name)
				.metadata(PresentationMetadata.builder()
						.displayName("Proxy")
						.description("Accepts the player's connection and routes it to Lobby or Game. "
								+ "Restart tests verify that the player can reconnect through the replacement proxy.")
						.build())
				.platform(platform)
				.distribution(distribution)
				.javaSelection(java)
				.server(defaultServer)
				.server(otherServer)
				.defaultServer(defaultServer)
				.build();
	}

	/**
	 * Requests the Java the live matrix runs a process on, which its version data calls verified.
	 */
	private static JavaSelection java(int featureVersion) {
		return JavaSelection.builder()
				.requirement(JavaRequirement.builder().featureVersion(featureVersion).build())
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
