package me.whereareiam.anvil.platform.neoforge;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgePlatformProviderTest {
	private static final String CHECKSUM = "a".repeat(64);

	@TempDir
	Path temporary;

	private final NeoForgePlatformProvider provider = new NeoForgePlatformProvider();
	private final List<String> requests = new ArrayList<>();

	@ParameterizedTest(name = "NeoForge {1} is a release for Minecraft {0}")
	@CsvSource({"1.21.1, 21.1.256", "1.21, 21.0.167", "1.21.11, 21.11.45", "26.1.2, 26.1.2.114", "26.1, 26.1.0.5-beta"})
	void acceptsTheNeoForgeReleasesOfTheMinecraftVersion(String version, String release) {
		assertDoesNotThrow(() -> provider.validateDistribution(server(Distribution.remote(version, release))));
	}

	@Test
	void refusesSelectorsThatDoNotNameOneReleaseOfTheMinecraftVersion() {
		assertEquals("NeoForge release 21.11.45 is not a release for Minecraft 1.21.1, whose releases start with 21.1.",
				refusal(Distribution.remote("1.21.1", "21.11.45")));
		assertTrue(refusal(Distribution.remote("1.21.1", "latest")).startsWith("NeoForge requires an exact NeoForge release"));
		assertTrue(refusal(Distribution.pinned("1.21.1", CHECKSUM)).startsWith("NeoForge requires an exact NeoForge release"));
		assertEquals("NeoForge requires an exact numeric Minecraft version", refusal(Distribution.remote("latest", "21.1.256")));
		assertEquals("NeoForge installer pin must be a 64-digit SHA-256",
				refusal(Distribution.builder().version("1.21.1").build("21.1.256").sha256("abc").build()));
	}

	@Test
	void resolvesTheInstalledServerOfAReleaseWithoutRunningItsInstallerAgain() throws IOException {
		Path installation = installed("21.1.256");
		MinecraftServer server = server(Distribution.remote("1.21.1", "21.1.256"));

		var resolved = provider.resolve(server, context(server, ForwardingMode.NONE));

		assertEquals(installation.resolve("server/server.jar"), resolved.getJar());
		assertEquals("NeoForge 21.1.256 for Minecraft 1.21.1", resolved.getDescription());
		String installer = "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.256/neoforge-21.1.256-installer.jar";
		assertEquals(List.of(
				"read " + installer + ".sha256",
				"obtain " + installer + " -> " + installation.resolve("neoforge-21.1.256-installer.jar") + " " + CHECKSUM
		), requests);
	}

	@Test
	void linksTheInstalledServerIntoTheWorkspaceAndKeepsUnrelatedProperties() throws IOException {
		installed("21.1.256");
		MinecraftServer server = server(Distribution.remote("1.21.1", "21.1.256"));
		PlatformContext context = context(server, ForwardingMode.NONE);
		Path work = context.getWorkDirectory();
		Files.writeString(work.resolve("server.properties"), "level-name=retained\nserver-port=1\n");

		provider.configure(server, context);
		provider.configure(server, context);

		assertEquals("library", Files.readString(work.resolve("libraries/net/neoforged/library.jar")));
		assertEquals("run", Files.readString(work.resolve("run.sh")));
		assertFalse(Files.exists(work.resolve("server.jar")), "The starter JAR is launched from the cache");
		assertFalse(Files.exists(work.resolve("installer.jar.log")));
		String properties = Files.readString(work.resolve("server.properties"));
		assertTrue(properties.contains("server-port=25566"), properties);
		assertTrue(properties.contains("level-name=retained"), properties);
		assertEquals("eula=true\n", Files.readString(work.resolve("eula.txt")));
	}

	@Test
	void refusesIdentityForwardingAndDeclaresItsAgentAsAMod() throws IOException {
		installed("21.1.256");
		MinecraftServer server = server(Distribution.remote("1.21.1", "21.1.256"));

		PlatformException failure = assertThrows(PlatformException.class,
				() -> provider.configure(server, context(server, ForwardingMode.MODERN)));

		assertEquals("NeoForge does not support identity forwarding", failure.getMessage());
		assertEquals(List.of(ForwardingMode.NONE), provider.forwardingModes());
		assertEquals(Path.of("mods", "anvil-platform-agent.jar"), provider.platformAgent().getTarget());
		assertEquals(List.of("nogui"), provider.programArguments(server));
	}

	@Test
	void packagesItsVersionDataBesideTheProvider() {
		var data = provider.versionData();

		assertNotNull(data, "neoforge-versions.toml is packaged with the provider");
		assertTrue(data.getPath().endsWith("me/whereareiam/anvil/platform/neoforge/neoforge-versions.toml"), data.toString());
	}

	private String refusal(Distribution distribution) {
		return assertThrows(PlatformException.class, () -> provider.validateDistribution(server(distribution))).getMessage();
	}

	private MinecraftServer server(Distribution distribution) {
		return MinecraftServer.builder().name("server").platform(Platforms.NEOFORGE).distribution(distribution).build();
	}

	/**
	 * Creates the cache content a finished installation leaves behind, so that no installer runs.
	 */
	private Path installed(String release) throws IOException {
		Path installation = temporary.resolve("cache/distributions/neoforge").resolve(release);
		Path server = installation.resolve("server");
		Files.createDirectories(server.resolve("libraries/net/neoforged"));
		Files.writeString(server.resolve("libraries/net/neoforged/library.jar"), "library");
		Files.writeString(server.resolve("run.sh"), "run");
		Files.writeString(server.resolve("server.jar"), "starter");
		Files.writeString(server.resolve("installer.jar.log"), "log");
		return installation;
	}

	private PlatformContext context(MinecraftServer server, ForwardingMode forwarding) throws IOException {
		Path work = Files.createDirectories(temporary.resolve("server"));
		return PlatformContext.builder()
				.scenario(AnvilScenario.builder().name("neoforge").entrypoint(server.getName()).server(server).build())
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(work)
				.bindAddress("127.0.0.1")
				.port(25566)
				.eulaAccepted(true)
				.artifactSource(artifactSource())
				.forwarding(ForwardingConfiguration.builder().mode(forwarding).build())
				.build();
	}

	private PlatformArtifactSource artifactSource() {
		return new PlatformArtifactSource() {
			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, String expectedSha256) {
				requests.add("obtain " + uri + " -> " + destination + " " + expectedSha256);
				return destination;
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				requests.add("read " + uri);
				return CHECKSUM + "\n";
			}
		};
	}
}
