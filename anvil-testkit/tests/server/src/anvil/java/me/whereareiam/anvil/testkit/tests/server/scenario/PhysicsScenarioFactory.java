package me.whereareiam.anvil.testkit.tests.server.scenario;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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
import me.whereareiam.anvil.api.type.ProcessLifetime;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Internal scenario factory of the physics suite, from {@code physics-matrix.toml}: for each release whose players run
 * client physics, the release's direct Paper server, the same server with the Grim anti-cheat, and a Velocity proxy
 * with the Sonar anti-bot in front of a Paper backend. The servers load the fixture plugin, whose console reports the
 * positions the server forces on a player, and are kept for the test JVM; each Sonar proxy starts fresh, so no
 * player is remembered as verified from an earlier test.
 *
 * <p>Grim and Sonar run with their own default configuration, read from the pinned JAR, with only these changes:
 * neither looks for updates, Grim prints every flag to the console, and Sonar leaves out its vehicle check, which
 * needs a player that steers a boat. Both run with an English locale, so their console lines and messages are those
 * the tests read.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhysicsScenarioFactory {
	/**
	 * Name of the server in the vanilla and Grim scenarios.
	 */
	public static final String SERVER = "server";
	/**
	 * Name of the Paper backend behind the Sonar proxy.
	 */
	public static final String BACKEND = "backend";
	/**
	 * Name of the Sonar proxy.
	 */
	public static final String PROXY = "proxy";

	private static final PhysicsMatrix MATRIX = PhysicsMatrix.read();
	private static final List<String> ENGLISH = List.of("-Duser.language=en", "-Duser.country=US");

	/**
	 * Returns the Minecraft versions of the physics suite's releases, oldest first.
	 *
	 * @return release versions
	 */
	public static @NotNull List<MinecraftVersion> releases() {
		return MATRIX.getReleases().stream().map(PhysicsMatrix.Release::minecraft).toList();
	}

	/**
	 * Tells whether the pinned Grim build supports a release of the physics suite.
	 *
	 * @param version Minecraft version of the release
	 * @return whether Grim runs on the release's Paper server
	 * @throws IllegalArgumentException when the physics suite has no such release
	 */
	public static boolean supportsGrim(@NotNull MinecraftVersion version) {
		return release(version).grim();
	}

	/**
	 * Tells whether the pinned Sonar build supports a release of the physics suite.
	 *
	 * @param version Minecraft version of the release
	 * @return whether Sonar verifies players of the release
	 * @throws IllegalArgumentException when the physics suite has no such release
	 */
	public static boolean supportsSonar(@NotNull MinecraftVersion version) {
		return release(version).sonar();
	}

	/**
	 * Returns the scenario of a release's direct Paper server, the judge of vanilla acceptance.
	 *
	 * @param version Minecraft version of the release
	 * @return the scenario
	 * @throws IllegalArgumentException when the physics suite has no such release
	 */
	public static @NotNull AnvilScenario vanillaScenario(@NotNull MinecraftVersion version) {
		release(version);
		MinecraftServer server = paper(version).toBuilder()
				.name(SERVER)
				.lifetime(ProcessLifetime.ENGINE)
				.metadata(PresentationMetadata.builder()
						.displayName("Physics course")
						.description("Paper server whose console builds the physics course and reports every position "
								+ "it forces on a player.")
						.build())
				.build();

		return scenario("physics-vanilla-" + version, "Vanilla physics · Paper " + version, server);
	}

	/**
	 * Returns the scenario of a release's Paper server with the Grim anti-cheat, which predicts every movement of a
	 * player exactly and prints each flag to the console.
	 *
	 * @param version Minecraft version of the release
	 * @return the scenario
	 * @throws IllegalArgumentException when the physics suite has no such release or Grim does not support it
	 */
	public static @NotNull AnvilScenario grimScenario(@NotNull MinecraftVersion version) {
		PhysicsMatrix.Release release = release(version);
		if (!release.grim())
			throw new IllegalArgumentException("The pinned Grim build does not support " + version);

		MinecraftServer paper = paper(version);
		Path grim = FixtureArtifacts.grim();
		String configuration = edit(read(grim, "config/en.yml"), "Grim's config.yml",
				new Edit("check-for-updates: true", "check-for-updates: false"),
				new Edit("verbose:\n    print-to-console: false", "verbose:\n    print-to-console: true"));
		MinecraftServer.MinecraftServerBuilder server = paper.toBuilder()
				.name(SERVER)
				.lifetime(ProcessLifetime.ENGINE)
				.memoryMegabytes(1024)
				.jvmArguments(ENGLISH)
				.metadata(PresentationMetadata.builder()
						.displayName("Physics course with Grim")
						.description("Paper server with the Grim anti-cheat, which prints every movement it flags.")
						.build())
				.workspace(paper.getWorkspace().toBuilder()
						.asset(WorkspaceAsset.builder()
								.group("grim")
								.source(AssetSource.path(grim))
								.target(Path.of("plugins", grim.getFileName().toString()))
								.build())
						.asset(WorkspaceAsset.builder()
								.group("grim-configuration")
								.source(AssetSource.text(configuration))
								.target(Path.of("plugins", "GrimAC", "config.yml"))
								.build())
						.build());
		if (release.grimJava() != null)
			server.javaSelection(JavaSelection.builder()
					.requirement(JavaRequirement.builder().featureVersion(release.grimJava()).build())
					.build());

		return scenario("physics-grim-" + version, "Grim physics · Paper " + version, server.build());
	}

	/**
	 * Returns the scenario of a Velocity proxy with the Sonar anti-bot in front of a release's Paper server. Sonar
	 * holds each new player in a world of its own, checks that the player falls with gravity and lands on a platform,
	 * and disconnects it with the outcome; a player that passed reaches the backend when it connects again.
	 *
	 * @param version Minecraft version of the release
	 * @return the scenario
	 * @throws IllegalArgumentException when the physics suite has no such release or Sonar does not support it
	 */
	public static @NotNull AnvilScenario sonarScenario(@NotNull MinecraftVersion version) {
		if (!release(version).sonar())
			throw new IllegalArgumentException("The pinned Sonar build does not support " + version);

		MinecraftServer backend = paper(version).toBuilder()
				.name(BACKEND)
				.lifetime(ProcessLifetime.ENGINE)
				.metadata(PresentationMetadata.builder()
						.displayName("Backend")
						.description("Paper server a player reaches once Sonar verified it.")
						.build())
				.build();
		Path sonar = FixtureArtifacts.sonar();
		String configuration = edit(read(sonar, "assets/config/en.yml"), "Sonar's config.yml",
				new Edit("check-for-updates: true", "check-for-updates: false"),
				new Edit("    vehicle:\n      # Should Sonar check if the player properly enters a boat?\n      timing: ALWAYS",
						"    vehicle:\n      # Should Sonar check if the player properly enters a boat?\n      timing: NEVER"));
		LiveMatrix.Proxies proxies = LiveMatrix.read().getProxies();
		MinecraftProxy proxy = MinecraftProxy.builder()
				.name(PROXY)
				.metadata(PresentationMetadata.builder()
						.displayName("Sonar proxy")
						.description("Velocity proxy whose Sonar plugin verifies every new player before it lets the "
								+ "player through to the backend.")
						.build())
				.platform(Platforms.VELOCITY)
				.distribution(Distribution.remote(proxies.velocityVersion(), proxies.velocityBuild()))
				.jvmArguments(ENGLISH)
				.workspace(WorkspacePlan.builder()
						.asset(WorkspaceAsset.builder()
								.group("sonar")
								.source(AssetSource.path(sonar))
								.target(Path.of("plugins", sonar.getFileName().toString()))
								.build())
						.asset(WorkspaceAsset.builder()
								.group("sonar-configuration")
								.source(AssetSource.text(configuration))
								.target(Path.of("plugins", "sonar", "config.yml"))
								.build())
						.build())
				.server(BACKEND)
				.defaultServer(BACKEND)
				.build();

		return AnvilScenario.builder()
				.name("physics-sonar-" + version)
				.metadata(PresentationMetadata.builder()
						.displayName("Sonar physics · Velocity / Paper " + version)
						.description("Sonar verifies a joining player's gravity and collision before the proxy lets it "
								+ "through to the backend.")
						.category("Client physics")
						.tag("physics")
						.build())
				.entrypoint(proxy.getName())
				.server(backend)
				.proxy(proxy)
				.build();
	}

	private static PhysicsMatrix.Release release(MinecraftVersion version) {
		return MATRIX.release(version)
				.orElseThrow(() -> new IllegalArgumentException("The physics matrix has no release " + version
						+ "; it runs " + releases()));
	}

	private static MinecraftServer paper(MinecraftVersion version) {
		return CompatibilityScenarioFactory.releaseScenario(version).getServers().getFirst();
	}

	private static AnvilScenario scenario(String name, String displayName, MinecraftServer server) {
		return AnvilScenario.builder()
				.name(name)
				.metadata(PresentationMetadata.builder()
						.displayName(displayName)
						.description("A course of falls, surfaces, steps, obstacles, water and a ladder that a player "
								+ "with client physics walks while the server judges each movement.")
						.category("Client physics")
						.tag("physics")
						.build())
				.entrypoint(server.getName())
				.server(server)
				.build();
	}

	private static String read(Path jar, String entry) {
		try (ZipFile archive = new ZipFile(jar.toFile())) {
			ZipEntry found = archive.getEntry(entry);
			if (found == null) throw new IllegalStateException(jar.getFileName() + " has no " + entry);

			try (InputStream input = archive.getInputStream(found)) {
				return new String(input.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read " + entry + " from " + jar, failure);
		}
	}

	private static String edit(String text, String file, Edit... edits) {
		String edited = text;
		for (Edit edit : edits) {
			int first = edited.indexOf(edit.from());
			if (first < 0 || first != edited.lastIndexOf(edit.from()))
				throw new IllegalStateException(file + " of the pinned build does not contain exactly one '"
						+ edit.from() + "'; check the edits when changing the pin");

			edited = edited.replace(edit.from(), edit.to());
		}

		return edited;
	}

	/**
	 * Replaces text that a default configuration file contains exactly once.
	 *
	 * @param from the default text
	 * @param to its replacement
	 */
	private record Edit(String from, String to) {
	}
}
