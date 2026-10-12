package me.whereareiam.anvil.platform.lumi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumiPlatformProviderTest {
	private static final String CHECKSUM = "a".repeat(64);

	@TempDir
	Path temporary;

	private final LumiPlatformProvider provider = new LumiPlatformProvider();
	private final YAMLMapper yaml = new YAMLMapper();
	private final List<String> requests = new ArrayList<>();

	@ParameterizedTest(name = "Lumi {1} accepts Minecraft {0}")
	@CsvSource({"1.20.0, 1.6.9", "1.21.0, 1.6.9", "1.21.130, 1.6.9", "1.26.50, 1.6.9"})
	void acceptsTheVersionsOfAListedRelease(String version, String release) {
		assertDoesNotThrow(() -> provider.validateDistribution(server(Distribution.remote(version, release))));
	}

	@Test
	void refusesSelectorsThatDoNotNameOneReleaseServingTheVersion() {
		assertTrue(refusal(Distribution.remote("1.26.50", "latest")).startsWith("Lumi requires an exact Lumi release"));
		assertTrue(refusal(Distribution.pinned("1.26.50", CHECKSUM)).startsWith("Lumi requires an exact Lumi release"));
		assertEquals("Lumi requires an exact numeric Minecraft version", refusal(Distribution.remote("latest", "1.6.9")));
		assertEquals("Lumi release 1.0.0 is not in Anvil's Lumi version data; known releases are [1.6.9]",
				refusal(Distribution.remote("1.26.50", "1.0.0")));
		assertTrue(refusal(Distribution.remote("1.19.80", "1.6.9")).startsWith("Lumi knows no protocol of Minecraft 1.19.80"));
		assertTrue(refusal(Distribution.remote("1.26.60", "1.6.9")).startsWith("Lumi knows no protocol of Minecraft 1.26.60"));
		// Lumi and CloudburstMC Protocol disagree on the protocol of 1.26.45, so no player could join it.
		assertTrue(refusal(Distribution.remote("1.26.45", "1.6.9")).startsWith("Lumi knows no protocol of Minecraft 1.26.45"));
		assertEquals("Lumi JAR pin must be a 64-digit SHA-256",
				refusal(Distribution.builder().version("1.26.50").build("1.6.9").sha256("abc").build()));
	}

	@Test
	void downloadsTheReleaseFromLumisMavenRepositoryVerifiedAgainstItsChecksum() throws IOException {
		MinecraftServer server = server(Distribution.remote("1.26.50", "1.6.9"));

		var resolved = provider.resolve(server, context(server, ForwardingMode.NONE));

		Path jar = temporary.resolve("cache/distributions/lumi/1.6.9/Lumi-1.6.9.jar");
		String source = "https://repo.lumi.su/releases/com/koshakmine/Lumi/1.6.9/Lumi-1.6.9.jar";
		assertEquals(jar, resolved.getJar());
		assertEquals("Lumi 1.6.9 for Minecraft 1.26.50", resolved.getDescription());
		assertEquals(List.of("read " + source + ".sha256", "obtain " + source + " -> " + jar + " " + CHECKSUM), requests);
	}

	@Test
	void usesAnExplicitPinInsteadOfTheRepositoryChecksum() throws IOException {
		String pin = "B".repeat(64);
		MinecraftServer server = server(Distribution.builder().version("1.26.50").build("1.6.9").sha256(pin).build());

		provider.resolve(server, context(server, ForwardingMode.NONE));

		assertEquals(1, requests.size(), requests.toString());
		assertTrue(requests.getFirst().endsWith(" " + "b".repeat(64)), requests.toString());
	}

	@Test
	void writesSettingsWithTheVersionsProtocolAndKeepsUnrelatedValues() throws IOException {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.LUMI)
				.distribution(Distribution.remote("1.21.0", "1.6.9"))
				.onlineMode(false)
				.setting("world.spawn-protection", "4")
				.setting("player.default-gamemode", "1")
				.build();
		PlatformContext context = context(server, ForwardingMode.NONE);
		Path file = context.getWorkDirectory().resolve("settings.yml");
		Files.writeString(file, """
				general:
				  max-players: 7
				  server-port: 1
				world:
				  spawn-protection: 10
				""");

		provider.configure(server, context);
		provider.configure(server, context);

		JsonNode settings = yaml.readTree(file.toFile());
		assertEquals(7, settings.at("/general/max-players").intValue());
		assertEquals("127.0.0.1", settings.at("/general/server-ip").asText());
		assertEquals(19133, settings.at("/general/server-port").intValue());
		assertEquals(685, settings.at("/general/multiversion/min-protocol").intValue());
		assertEquals(685, settings.at("/general/multiversion/max-protocol").intValue());
		assertEquals("eng", settings.at("/general/language").asText());
		assertEquals("Anvil: server", settings.at("/general/motd").asText());
		assertEquals(false, settings.at("/network/xbox-auth").booleanValue());
		assertEquals(false, settings.at("/network/enable-query").booleanValue());
		assertEquals(false, settings.at("/network/rcon/enable").booleanValue());
		assertEquals(false, settings.at("/bstats/enable").booleanValue());
		assertEquals(4, settings.at("/world/spawn-protection").intValue());
		assertEquals(1, settings.at("/player/default-gamemode").intValue());
	}

	@Test
	void defaultsSpawnProtectionToZeroAndAuthenticatesWithXboxForAnOnlineServer() throws IOException {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.LUMI)
				.distribution(Distribution.remote("1.26.50", "1.6.9"))
				.onlineMode(true)
				.build();
		PlatformContext context = context(server, ForwardingMode.NONE);

		provider.configure(server, context);

		JsonNode settings = yaml.readTree(context.getWorkDirectory().resolve("settings.yml").toFile());
		assertEquals(0, settings.at("/world/spawn-protection").intValue());
		assertEquals(true, settings.at("/network/xbox-auth").booleanValue());
		assertEquals(2193, settings.at("/general/multiversion/max-protocol").intValue());
	}

	@Test
	void refusesRuntimeOwnedSettingsIdentityForwardingAndAnotherSessionServer() throws IOException {
		MinecraftServer owned = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.LUMI)
				.distribution(Distribution.remote("1.26.50", "1.6.9"))
				.setting("general.multiversion.max-protocol", "1")
				.build();
		PlatformException setting = assertThrows(PlatformException.class,
				() -> provider.configure(owned, context(owned, ForwardingMode.NONE)));
		assertEquals("Lumi setting 'general.multiversion.max-protocol' is owned by Anvil runtime configuration",
				setting.getMessage());

		MinecraftServer server = server(Distribution.remote("1.26.50", "1.6.9"));
		PlatformException forwarding = assertThrows(PlatformException.class,
				() -> provider.configure(server, context(server, ForwardingMode.MODERN)));
		assertEquals("Lumi does not support identity forwarding", forwarding.getMessage());
		assertEquals(List.of(ForwardingMode.NONE), provider.forwardingModes());
		assertThrows(PlatformException.class,
				() -> provider.sessionServerArguments(server, URI.create("http://127.0.0.1:1")));
	}

	@Test
	void listensOnUdpStopsWithStopAndInstallsItsAgentAsAPlugin() {
		assertEquals(ListenerTransport.UDP, provider.gameTransport());
		assertEquals("stop", provider.stopCommand());
		assertEquals(Path.of("plugins", "anvil-platform-agent.jar"), provider.platformAgent().getTarget());
		assertTrue(provider.readinessPattern().matcher("[19:20:05 INFO ] Done (3.21s)! For help, type \"help\"").find());
	}

	@Test
	void packagesVersionDataWhoseKnownVersionsAreExactlyTheVersionsWithAProtocol() throws IOException {
		var data = provider.versionData();
		assertNotNull(data, "lumi-versions.toml is packaged with the provider");
		assertTrue(data.getPath().endsWith("me/whereareiam/anvil/platform/lumi/lumi-versions.toml"), data.toString());

		JsonNode document;
		try (InputStream input = data.openStream()) {
			document = new TomlMapper().readTree(input);
		}
		assertEquals("bedrock", document.path("edition").asText());
		List<MinecraftVersion> known = new ArrayList<>();
		document.path("known").forEach(version -> known.add(MinecraftVersion.parse(version.asText())));
		List<MinecraftVersion> protocols = new ArrayList<>();
		for (Map.Entry<String, JsonNode> entry : document.at("/lumi/protocol").properties())
			protocols.add(MinecraftVersion.parse(entry.getKey()));
		assertEquals(protocols, known);
	}

	private String refusal(Distribution distribution) {
		return assertThrows(PlatformException.class, () -> provider.validateDistribution(server(distribution))).getMessage();
	}

	private MinecraftServer server(Distribution distribution) {
		return MinecraftServer.builder().name("server").platform(Platforms.LUMI).distribution(distribution).build();
	}

	private PlatformContext context(MinecraftServer server, ForwardingMode forwarding) throws IOException {
		Path work = Files.createDirectories(temporary.resolve("server"));
		return PlatformContext.builder()
				.scenario(AnvilScenario.builder().name("lumi").entrypoint(server.getName()).server(server).build())
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(work)
				.bindAddress("127.0.0.1")
				.port(19133)
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
