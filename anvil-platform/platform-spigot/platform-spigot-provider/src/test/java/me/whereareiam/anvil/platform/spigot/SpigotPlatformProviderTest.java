package me.whereareiam.anvil.platform.spigot;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactResolver;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
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

class SpigotPlatformProviderTest {
	@TempDir
	Path temporary;

	@Test
	void configuresLegacyForwardingForProxy() throws Exception {
		Path jar = Files.writeString(temporary.resolve("spigot.jar"), "jar");
		Path work = Files.createDirectory(temporary.resolve("server"));
		Files.writeString(work.resolve("spigot.yml"), "settings:\n  bungeecord: false\n  sample: retained\n");
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.SPIGOT)
				.minecraftVersion("1.21.11")
				.distribution(Distribution.local(jar))
				.build();
		MinecraftProxy proxy = MinecraftProxy.builder()
				.name("proxy")
				.platform(Platforms.BUNGEECORD)
				.distribution(Distribution.remote("BungeeCord", "1"))
				.server(server.getName())
				.defaultServer(server.getName())
				.build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("spigot")
				.entrypoint(proxy.getName())
				.server(server)
				.proxy(proxy)
				.build();
		PlatformContext context = PlatformContext.builder()
				.scenario(scenario)
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(work)
				.bindAddress("127.0.0.1")
				.port(25566)
				.processAddresses(Map.of("proxy", new java.net.InetSocketAddress("127.0.0.1", 25565), "server", new java.net.InetSocketAddress("127.0.0.1", 25566)))
				.eulaAccepted(true)
				.artifactResolver(artifactResolver())
				.forwarding(ForwardingConfiguration.builder().mode(ForwardingMode.LEGACY).build())
				.build();

		SpigotPlatformProvider provider = new SpigotPlatformProvider();
		assertEquals(jar.toAbsolutePath(), provider.resolve(server, context).getJar());
		provider.configure(server, context);

		assertTrue(Files.readString(work.resolve("spigot.yml")).contains("bungeecord: true"));
		assertTrue(Files.readString(work.resolve("server.properties")).contains("server-port=25566"));
		provider.configure(server, context);
		assertEquals("retained", new YAMLMapper().readTree(work.resolve("spigot.yml").toFile()).at("/settings/sample").asText());
		provider.configure(server, context.toBuilder().forwarding(ForwardingConfiguration.builder().build()).build());
		assertEquals(false, new YAMLMapper().readTree(work.resolve("spigot.yml").toFile()).at("/settings/bungeecord").asBoolean());
	}

	private ArtifactResolver artifactResolver() {
		return new ArtifactResolver() {
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
