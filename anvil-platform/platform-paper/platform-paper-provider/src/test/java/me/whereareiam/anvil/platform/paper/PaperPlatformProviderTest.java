package me.whereareiam.anvil.platform.paper;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.ArtifactResolver;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPlatformProviderTest {
	@TempDir
	Path temporary;

	@Test
	void writesLegacyForwardingLayoutForPaper118() throws Exception {
		Path work = Files.createDirectory(temporary.resolve("legacy"));
		Files.writeString(work.resolve("paper.yml"), "settings:\n  legacy-setting: preserved\n");
		MinecraftServer server = MinecraftServer.builder().name("server").platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.18.2", "388")).build();
		AnvilScenario scenario = AnvilScenario.builder().name("legacy").entrypoint("server").server(server).build();
		PlatformContext context = context(scenario, work, Map.of("server", 25565), 25565);
		PaperPlatformProvider provider = new PaperPlatformProvider();
		provider.configure(server, context);
		var yaml = new YAMLMapper();
		var paper = yaml.readTree(work.resolve("paper.yml").toFile());
		assertEquals("test-secret", paper.at("/settings/velocity-support/secret").asText());
		assertTrue(paper.at("/settings/velocity-support/enabled").asBoolean());
		assertEquals("preserved", paper.at("/settings/legacy-setting").asText());
		assertEquals(false, Files.exists(work.resolve("config/paper-global.yml")));
		provider.configure(server, context.toBuilder().forwarding(ForwardingConfiguration.builder().build()).build());
		paper = yaml.readTree(work.resolve("paper.yml").toFile());
		assertEquals(false, paper.at("/settings/velocity-support/enabled").asBoolean());
		assertTrue(paper.at("/settings/velocity-support/secret").isMissingNode());
	}

	@Test
	void resolvesLocalArtifactAndAppliesRuntimeValuesAfterOverlay() throws Exception {
		Path localJar = Files.writeString(temporary.resolve("paper.jar"), "jar");
		Path run = Files.createDirectory(temporary.resolve("run"));
		Path work = Files.createDirectory(run.resolve("server"));
		Files.writeString(work.resolve("server.properties"), "server-port=1\noverlay-value=present\n");
		Files.createDirectories(work.resolve("config"));
		Files.writeString(work.resolve("config/paper-global.yml"), "proxies:\n  proxy-protocol: false\n  velocity:\n    enabled: false\nother: retained\n");
		Files.writeString(work.resolve("spigot.yml"), "settings:\n  sample: retained\n  bungeecord: true\n");
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform(Platforms.PAPER)
				.minecraftVersion("1.21.11")
				.distribution(Distribution.local(localJar))
				.setting("server-port", "2")
				.setting("declared-value", "present")
				.build();
		MinecraftProxy proxy = MinecraftProxy.builder()
				.name("proxy")
				.platform(Platforms.VELOCITY)
				.distribution(Distribution.remote("3.5.1", "615"))
				.onlineMode(true)
				.server(server.getName())
				.defaultServer(server.getName())
				.build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("paper-forwarding")
				.entrypoint(proxy.getName())
				.server(server)
				.proxy(proxy)
				.build();
		PlatformContext context = context(scenario, work, Map.of("proxy", 25565, "server", 25566), 25566);

		PaperPlatformProvider provider = new PaperPlatformProvider();
		assertEquals(localJar.toAbsolutePath(), provider.resolve(server, context).getJar());
		provider.configure(server, context);

		Properties properties = new Properties();
		properties.load(new StringReader(Files.readString(work.resolve("server.properties"))));
		assertEquals("25566", properties.getProperty("server-port"));
		assertEquals("present", properties.getProperty("overlay-value"));
		assertEquals("present", properties.getProperty("declared-value"));
		assertTrue(Files.readString(work.resolve("config/paper-global.yml")).contains("online-mode: true"));
		assertTrue(Files.readString(work.resolve("eula.txt")).contains("eula=true"));
		provider.configure(server, context);
		var yaml = new YAMLMapper();
		var configured = yaml.readTree(work.resolve("config/paper-global.yml").toFile());
		assertEquals("retained", configured.path("other").asText());
		assertEquals("test-secret", configured.at("/proxies/velocity/secret").asText());
		provider.configure(server, context.toBuilder().forwarding(ForwardingConfiguration.builder().build()).build());
		configured = yaml.readTree(work.resolve("config/paper-global.yml").toFile());
		assertEquals(false, configured.at("/proxies/velocity/enabled").asBoolean());
		assertTrue(configured.at("/proxies/velocity/secret").isMissingNode());
		assertEquals("retained", yaml.readTree(work.resolve("spigot.yml").toFile()).at("/settings/sample").asText());
	}

	private PlatformContext context(
			AnvilScenario scenario,
			Path work,
			Map<String, Integer> ports,
			int port
	) {
		return PlatformContext.builder()
				.scenario(scenario)
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(work)
				.bindAddress("127.0.0.1")
				.port(port)
				.processPorts(ports)
				.javaExecutable(Path.of(System.getProperty("java.home"), "bin", "java"))
				.eulaAccepted(true)
				.artifactResolver(artifactResolver())
				.forwarding(ForwardingConfiguration.builder().mode(ForwardingMode.MODERN)
						.proxyOnlineMode(true).secret("test-secret").build())
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
