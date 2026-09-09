package me.whereareiam.anvil.platform.bungeecord;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BungeeCordPlatformProviderTest {
	@TempDir
	Path temporary;

	@Test
	void resolvesLocalArtifactAndGeneratesLegacyNetwork() throws Exception {
		Path jar = Files.writeString(temporary.resolve("bungee.jar"), "jar");
		Path work = Files.createDirectory(temporary.resolve("proxy"));
		Files.writeString(work.resolve("config.yml"), "custom: retained\nservers:\n  stale: {address: 'localhost:1'}\n");
		MinecraftServer server = MinecraftServer.builder()
				.name("lobby")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.build();
		MinecraftProxy proxy = MinecraftProxy.builder()
				.name("proxy's \"quoted\" name")
				.platform(Platforms.BUNGEECORD)
				.distribution(Distribution.local(jar))
				.server(server.getName())
				.defaultServer(server.getName())
				.build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("bungee")
				.entrypoint(proxy.getName())
				.server(server)
				.proxy(proxy)
				.build();
		PlatformContext context = PlatformContext.builder()
				.scenario(scenario)
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(work)
				.bindAddress("127.0.0.1")
				.port(25565)
				.processAddresses(Map.of("proxy", new java.net.InetSocketAddress("127.0.0.1", 25565), "lobby", new java.net.InetSocketAddress("127.0.0.1", 25566)))
				.eulaAccepted(true)
				.artifactSource(artifactSource())
				.forwarding(ForwardingConfiguration.builder().mode(ForwardingMode.LEGACY).build())
				.build();

		BungeeCordPlatformProvider provider = new BungeeCordPlatformProvider();
		assertEquals(jar.toAbsolutePath(), provider.resolve(proxy, context).getJar());
		provider.configure(proxy, context);

		var config = new YAMLMapper().readTree(work.resolve("config.yml").toFile());
		assertTrue(config.path("ip_forward").asBoolean());
		assertEquals("127.0.0.1:25566", config.at("/servers/lobby/address").asText());
		assertTrue(config.at("/servers/stale").isMissingNode());
		assertEquals("Anvil: " + proxy.getName(), config.at("/listeners/0/motd").asText());
		assertEquals("retained", config.path("custom").asText());
		provider.configure(proxy, context);
	}

	private PlatformArtifactSource artifactSource() {
		return new PlatformArtifactSource() {
			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, String expectedSha256) {
				throw new AssertionError("No remote artifact expected");
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				throw new AssertionError("No remote resource expected");
			}
		};
	}
}
