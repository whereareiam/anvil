package me.whereareiam.anvil.platform.velocity;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.ArtifactResolver;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityPlatformProviderTest {
	@TempDir
	Path temporary;

	@Test
	void generatesModernForwardingAndProtectsRuntimeSettings() throws Exception {
		Path jar = Files.writeString(temporary.resolve("velocity.jar"), "jar");
		Path run = Files.createDirectory(temporary.resolve("run"));
		Path work = Files.createDirectory(run.resolve("proxy"));
		Files.writeString(work.resolve("velocity.toml"), "custom = 'retained'\n[advanced]\nexisting = true\n");
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.build();
		MinecraftProxy proxy = proxy(jar, server.getName()).toBuilder()
				.setting("advanced.compression-threshold", "128")
				.setting("custom-list", "[1, 2, 3]")
				.setting("custom-string", "'value with \"quotes\"'")
				.build();
		AnvilScenario scenario = scenario(proxy, server);
		PlatformContext context = context(scenario, work);

		VelocityPlatformProvider provider = new VelocityPlatformProvider();
		assertEquals(jar.toAbsolutePath(), provider.resolve(proxy, context).getJar());
		provider.configure(proxy, context);
		var config = new TomlMapper().readTree(work.resolve("velocity.toml").toFile());
		assertEquals("modern", config.path("player-info-forwarding-mode").asText());
		assertEquals("127.0.0.1:25566", config.at("/servers/server").asText());
		assertEquals("test-secret", Files.readString(work.resolve("forwarding.secret")));
		assertEquals("retained", config.path("custom").asText());
		assertEquals(128, config.at("/advanced/compression-threshold").asInt());
		assertTrue(config.at("/advanced/existing").asBoolean());
		assertEquals(3, config.path("custom-list").size());
		assertEquals("value with \"quotes\"", config.path("custom-string").asText());
		provider.configure(proxy, context);

		MinecraftProxy invalid = proxy.toBuilder().setting("bind", "0.0.0.0:1").build();
		assertThrows(PlatformException.class, () -> provider.configure(invalid, context));
		assertThrows(PlatformException.class, () -> provider.configure(proxy.toBuilder().setting("custom", "unquoted text").build(), context));
		assertThrows(PlatformException.class, () -> provider.configure(proxy.toBuilder().setting("servers.fake", "'localhost:1'").build(), context));
	}

	private MinecraftProxy proxy(Path jar, String server) {
		return MinecraftProxy.builder()
				.name("proxy")
				.platform(Platforms.VELOCITY)
				.distribution(Distribution.local(jar))
				.server(server)
				.defaultServer(server)
				.build();
	}

	private AnvilScenario scenario(MinecraftProxy proxy, MinecraftServer server) {
		return AnvilScenario.builder()
				.name("velocity")
				.entrypoint(proxy.getName())
				.server(server)
				.proxy(proxy)
				.build();
	}

	private PlatformContext context(AnvilScenario scenario, Path work) {
		return PlatformContext.builder()
				.scenario(scenario)
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(work)
				.bindAddress("127.0.0.1")
				.port(25565)
				.processPorts(Map.of("proxy", 25565, "server", 25566))
				.javaExecutable(Path.of(System.getProperty("java.home"), "bin", "java"))
				.eulaAccepted(true)
				.artifactResolver(artifactResolver())
				.forwarding(ForwardingConfiguration.builder().mode(ForwardingMode.MODERN).secret("test-secret").build())
				.build();
	}

	private ArtifactResolver artifactResolver() {
		return new ArtifactResolver() {
			@Override
			public Path obtain(URI uri, Path destination, String expectedSha256) {
				throw new AssertionError("No remote artifact expected");
			}

			@Override
			public String read(URI uri) {
				throw new AssertionError("No remote resource expected");
			}
		};
	}
}
