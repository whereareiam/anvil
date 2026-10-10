package me.whereareiam.anvil.platform.paper;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.type.CacheIdentity;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PaperPlatformProviderTest {
	@TempDir
	Path temporary;

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
		PlatformContext context = context(scenario, work, Map.of("proxy", 25565, "server", 25566));

		PaperPlatformProvider provider = new PaperPlatformProvider();
		assertTrue(provider.jvmArguments(server, false).isEmpty(), "Ordinary engine runs retain native console defaults");
		assertEquals(List.of("-Dterminal.ansi=true", "-Dterminal.jline=false"),
				provider.jvmArguments(server, true));
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
        assertFalse(configured.at("/proxies/velocity/enabled").asBoolean());
		assertTrue(configured.at("/proxies/velocity/secret").isMissingNode());
		assertEquals("retained", yaml.readTree(work.resolve("spigot.yml").toFile()).at("/settings/sample").asText());
		assertFalse(Files.exists(work.resolve("paper.yml")), "Paper 1.19+ reads forwarding from paper-global.yml only");
	}

	@ParameterizedTest
	@CsvSource({"1.16.5", "1.17.1", "1.18.2"})
	void writesVelocitySupportToPaperYmlBeforeOneNineteen(String version) throws Exception {
		Path work = Files.createDirectories(temporary.resolve("legacy-" + version));
		Files.writeString(work.resolve("paper.yml"), "config-version: 20\nsettings:\n  velocity-support:\n    enabled: false\n  retained: true\n");
		MinecraftServer server = MinecraftServer.builder().name("server").platform(Platforms.PAPER)
				.distribution(Distribution.remote(version, "1")).build();
		AnvilScenario scenario = AnvilScenario.builder().name("legacy").entrypoint("server").server(server).build();
		PlatformContext modern = context(scenario, work, Map.of("server", 25566));
		PaperPlatformProvider provider = new PaperPlatformProvider();
		YAMLMapper yaml = new YAMLMapper();

		provider.configure(server, modern);
		var paper = yaml.readTree(work.resolve("paper.yml").toFile());
		assertTrue(paper.at("/settings/velocity-support/enabled").asBoolean());
		assertTrue(paper.at("/settings/velocity-support/online-mode").asBoolean());
		assertEquals("test-secret", paper.at("/settings/velocity-support/secret").asText());
		assertTrue(paper.at("/settings/retained").asBoolean());
		assertEquals(20, paper.path("config-version").asInt());
		assertFalse(yaml.readTree(work.resolve("spigot.yml").toFile()).at("/settings/bungeecord").asBoolean());
		assertFalse(Files.exists(work.resolve("config/paper-global.yml")), "Paper before 1.19 has no paper-global.yml");

		provider.configure(server, modern.toBuilder().forwarding(ForwardingConfiguration.builder()
				.mode(ForwardingMode.LEGACY).secret("legacy-secret").build()).build());
		paper = yaml.readTree(work.resolve("paper.yml").toFile());
		assertFalse(paper.at("/settings/velocity-support/enabled").asBoolean());
		assertTrue(paper.at("/settings/velocity-support/secret").isMissingNode());
		assertTrue(yaml.readTree(work.resolve("spigot.yml").toFile()).at("/settings/bungeecord").asBoolean());

		provider.configure(server, modern.toBuilder().forwarding(ForwardingConfiguration.builder().build()).build());
		paper = yaml.readTree(work.resolve("paper.yml").toFile());
		assertFalse(paper.at("/settings/velocity-support/enabled").asBoolean());
		assertFalse(yaml.readTree(work.resolve("spigot.yml").toFile()).at("/settings/bungeecord").asBoolean());
	}

	@Test
	void writesProxiesVelocityToPaperGlobalFromOneNineteen() throws Exception {
		Path work = Files.createDirectories(temporary.resolve("modern"));
		MinecraftServer server = MinecraftServer.builder().name("server").platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.19.4", "1")).build();
		AnvilScenario scenario = AnvilScenario.builder().name("modern").entrypoint("server").server(server).build();

		new PaperPlatformProvider().configure(server, context(scenario, work, Map.of("server", 25566)));
		var global = new YAMLMapper().readTree(work.resolve("config/paper-global.yml").toFile());
		assertTrue(global.at("/proxies/velocity/enabled").asBoolean());
		assertEquals("test-secret", global.at("/proxies/velocity/secret").asText());
		assertFalse(Files.exists(work.resolve("paper.yml")));
	}

	@Test
	void packagesItsVersionDataBesideTheProvider() {
		var data = new PaperPlatformProvider().versionData();

		assertNotNull(data, "paper-versions.toml is packaged with the provider");
		assertTrue(data.getPath().endsWith("me/whereareiam/anvil/platform/paper/paper-versions.toml"), data.toString());
	}

	@ParameterizedTest
	@CsvSource({"1.16.5, cache", "1.17.1, cache", "1.18.2, libraries versions cache", "1.21.11, libraries versions cache"})
	void cachesPaperclipOutputsForTheServerVersion(String version, String paths) {
		MinecraftServer server = MinecraftServer.builder().name("server").platform(Platforms.PAPER)
				.distribution(Distribution.remote(version, "1")).build();

		List<WorkspaceCache> caches = new PaperPlatformProvider().defaultCaches(server);
		assertEquals(Arrays.stream(paths.split(" ")).map(Path::of).toList(), caches.stream().map(WorkspaceCache::getPath).toList());
		assertTrue(caches.stream().allMatch(cache -> cache.getGroup().equals("paper")));
		assertTrue(caches.stream().allMatch(cache -> cache.getIdentity() == CacheIdentity.PROCESS),
				"Paperclip's outputs follow from the distribution, so a rebuilt plugin must not start new snapshots");
	}

	private PlatformContext context(
			AnvilScenario scenario,
			Path work,
			Map<String, Integer> ports
	) {
		return PlatformContext.builder()
				.scenario(scenario)
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(work)
				.bindAddress("127.0.0.1")
				.port(25566)
				.processAddresses(ports.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> new InetSocketAddress("127.0.0.1", entry.getValue()))))
				.eulaAccepted(true)
				.artifactSource(artifactSource())
				.forwarding(ForwardingConfiguration.builder().mode(ForwardingMode.MODERN)
						.proxyOnlineMode(true).secret("test-secret").build())
				.build();
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
