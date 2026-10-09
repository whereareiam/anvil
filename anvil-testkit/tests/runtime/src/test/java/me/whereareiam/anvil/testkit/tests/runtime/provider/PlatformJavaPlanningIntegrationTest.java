package me.whereareiam.anvil.testkit.tests.runtime.provider;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.SupportPolicy;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plans Java with the bundled platform providers and the version data they ship, through the
 * planner embedded in the launcher; every refusal happens before any artifact is downloaded.
 */
class PlatformJavaPlanningIntegrationTest {
	private static final Map<String, PlatformProvider> PROVIDERS = ServiceLoader.load(PlatformProvider.class).stream()
			.map(ServiceLoader.Provider::get)
			.collect(Collectors.toMap(PlatformProvider::id, Function.identity()));

	@Test
	void theLauncherJarCarriesThePlannerAndTheTomlParserItReadsVersionDataWith() throws Exception {
		// Providers on this class path bring their own TOML parser, so only the launcher JAR's entries prove the embedding.
		Path launcher = Path.of(AnvilLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());

		try (JarFile jar = new JarFile(launcher.toFile())) {
			assertNotNull(jar.getEntry("me/whereareiam/anvil/platform/planning/version/PlatformVersionsReader.class"), launcher.toString());
			assertNotNull(jar.getEntry("com/fasterxml/jackson/dataformat/toml/TomlMapper.class"), launcher.toString());
		}
	}

	@ParameterizedTest(name = "{0} {1} runs Java {2} by default")
	@CsvSource({
			"paper,  1.16.5,  11",
			"paper,  1.17.1,  17",
			"paper,  1.18.2,  17",
			"paper,  1.19.4,  17",
			"paper,  1.20.4,  17",
			"paper,  1.20.6,  21",
			"paper,  1.21.11, 21",
			"paper,  26.1,    25",
			"paper,  26.1.2,  25",
			"spigot, 1.16.5,  11",
			"spigot, 1.17.1,  17",
			"spigot, 1.18.2,  17",
			"spigot, 1.19.4,  17",
			"spigot, 1.20.6,  21",
			"spigot, 1.21.11, 21",
			"spigot, 26.1.2,  25"
	})
	void runsEveryKnownServerVersionOnItsPreferredLtsUnderTheStrictPolicy(String platform, String version, int java) {
		ProcessPlan plan = plan(server(platform, version, null), SupportPolicy.STRICT);

		assertEquals(java, plan.getJavaSelection().getRequirement().getFeatureVersion());
		assertTrue(plan.getJvmArguments().isEmpty(), plan.getJvmArguments().toString());
	}

	@ParameterizedTest(name = "Velocity {0} runs Java {1} by default")
	@CsvSource({"3.3.0, 21", "3.4.0, 21", "3.5.0-SNAPSHOT, 21", "3.5.1, 21"})
	void runsVelocityOnTheJavaItsAgentNeeds(String release, int java) {
		assertEquals(java, java(proxy(Platforms.VELOCITY, Distribution.remote(release, "1"), null), SupportPolicy.STRICT));
	}

	@Test
	void runsEveryBungeeCordBuildAndUnreleasedVelocityOnTheirRows() {
		assertEquals(21, java(proxy(Platforms.BUNGEECORD, Distribution.remote("BungeeCord", "2085"), null), SupportPolicy.STRICT));
		assertEquals(25, java(proxy(Platforms.VELOCITY, Distribution.remote("4.0.0", "1"), null), SupportPolicy.LENIENT));
		assertEquals(25, java(proxy(Platforms.VELOCITY, Distribution.local(Path.of("velocity.jar")), null), SupportPolicy.STRICT));
	}

	@Test
	void refusesJavaOlderThanAProxyAgentBeforeDownloading() {
		var velocity = assertThrows(ScenarioValidationException.class,
				() -> plan(proxy(Platforms.VELOCITY, Distribution.remote("3.4.0", "1"), 17), SupportPolicy.LENIENT));
		assertTrue(velocity.getMessage().contains("but the Anvil agent installed into velocity requires Java 21 or newer"),
				velocity.getMessage());

		var bungeecord = assertThrows(ScenarioValidationException.class,
				() -> plan(proxy(Platforms.BUNGEECORD, Distribution.remote("BungeeCord", "2085"), 17), SupportPolicy.LENIENT));
		assertTrue(bungeecord.getMessage().contains("but the Anvil agent installed into bungeecord requires Java 21 or newer"),
				bungeecord.getMessage());
	}

	@Test
	void runsPaperOneSixteenOnItsPreferredLtsAndOnJavaSeventeenThroughTheBypass() {
		ProcessPlan preferred = plan(server(Platforms.PAPER, "1.16.5", null), SupportPolicy.STRICT);
		assertEquals(11, preferred.getJavaSelection().getRequirement().getFeatureVersion());
		assertTrue(preferred.getJvmArguments().isEmpty(), preferred.getJvmArguments().toString());

		ProcessPlan bypassed = plan(server(Platforms.PAPER, "1.16.5", 17), SupportPolicy.LENIENT);
		assertEquals(17, bypassed.getJavaSelection().getRequirement().getFeatureVersion());
		assertEquals(List.of("-DPaper.IgnoreJavaVersion=true"), bypassed.getJvmArguments());

		ProcessPlan seventeen = plan(server(Platforms.PAPER, "1.17.1", 21), SupportPolicy.LENIENT);
		assertEquals(List.of("-DPaper.IgnoreJavaVersion=true"), seventeen.getJvmArguments());
	}

	@ParameterizedTest(name = "Spigot {0} refuses Java {1} above its maximum {2}")
	@CsvSource({"1.16.5, 17, 16", "1.17.1, 21, 17", "1.18.2, 21, 18", "1.19.4, 21, 20", "1.20.6, 25, 22"})
	void refusesSpigotAboveItsMaximum(String version, int java, int maximum) {
		var failure = assertThrows(ScenarioValidationException.class,
				() -> plan(server(Platforms.SPIGOT, version, java), SupportPolicy.LENIENT));

		assertTrue(failure.getMessage().contains("spigot " + version + " refuses Java above " + maximum + " and has no bypass"),
				failure.getMessage());
	}

	@Test
	void refusesSpigotOneSixteenOnJavaSeventeenAndPointsToPaper() {
		var failure = assertThrows(ScenarioValidationException.class,
				() -> plan(server(Platforms.SPIGOT, "1.16.5", 17), SupportPolicy.LENIENT));

		assertEquals("Process 'server' requests Java 17 (requested by process 'server'), but spigot 1.16.5 refuses Java "
				+ "above 16 and has no bypass. Remove the Java requirement to use Java 11 (default), or use 'paper', "
				+ "which runs Minecraft 1.16.5 on Java 17 with -DPaper.IgnoreJavaVersion=true.", failure.getMessage());
	}

	@Test
	void refusesUntestedBypassesUnderTheStrictPolicyOnly() {
		var strict = assertThrows(ScenarioValidationException.class,
				() -> plan(server(Platforms.PAPER, "1.16.5", 21), SupportPolicy.STRICT));
		assertTrue(strict.getMessage().contains("is UNTESTED"), strict.getMessage());

		ProcessPlan lenient = plan(server(Platforms.PAPER, "1.16.5", 21), SupportPolicy.LENIENT);
		assertEquals(List.of("-DPaper.IgnoreJavaVersion=true"), lenient.getJvmArguments());
	}

	@Test
	void refusesVersionsBeforeTheFirstRowAndNonLtsJavaBeforeDownloading() {
		var old = assertThrows(ScenarioValidationException.class,
				() -> plan(server(Platforms.PAPER, "1.16.4", null), SupportPolicy.LENIENT));
		assertTrue(old.getMessage().contains("paper 1.16.4 is UNSUPPORTED"), old.getMessage());

		var nonLts = assertThrows(ScenarioValidationException.class,
				() -> plan(server(Platforms.PAPER, "1.16.5", 16), SupportPolicy.LENIENT));
		assertTrue(nonLts.getMessage().contains("which is not an LTS release"), nonLts.getMessage());

		var legacy = assertThrows(ScenarioValidationException.class,
				() -> plan(server(Platforms.PAPER, "1.16.5", 8), SupportPolicy.LENIENT));
		assertTrue(legacy.getMessage().contains("requests Java 8 (requested by process 'server'), which is not an LTS release"),
				legacy.getMessage());
	}

	private static int java(MinecraftProcess process, SupportPolicy policy) {
		return plan(process, policy).getJavaSelection().getRequirement().getFeatureVersion();
	}

	private static ProcessPlan plan(MinecraftProcess process, SupportPolicy policy) {
		PlatformArtifactSource artifacts = new PlatformArtifactSource() {
			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String checksum) {
				throw new AssertionError("Planning must not download artifacts");
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				throw new AssertionError("Planning must not download metadata");
			}
		};
		var planner = new DefaultPlatformPlanner(EngineOptions.builder().supportPolicy(policy).build(), PROVIDERS,
				artifacts, agent -> Path.of("agent.jar"));
		var scenario = AnvilScenario.builder().name("java").entrypoint(process.getName());
		if (process instanceof MinecraftProxy proxy)
			scenario.proxy(proxy).server(server(Platforms.PAPER, "1.21.11", null));
		if (process instanceof MinecraftServer server)
			scenario.server(server);

		return planner.plan(scenario.build()).getProcesses().get(process.getName());
	}

	private static MinecraftServer server(String platform, String version, @Nullable Integer java) {
		Distribution distribution = Platforms.SPIGOT.equals(platform)
				? Distribution.pinned(version, "a".repeat(64))
				: Distribution.remote(version, "1");

		return MinecraftServer.builder().name("server").platform(platform).distribution(distribution)
				.javaSelection(selection(java)).build();
	}

	private static MinecraftProxy proxy(String platform, Distribution distribution, @Nullable Integer java) {
		return MinecraftProxy.builder().name("proxy").platform(platform).distribution(distribution)
				.server("server").defaultServer("server").javaSelection(selection(java)).build();
	}

	private static JavaSelection selection(@Nullable Integer java) {
		if (java == null) return JavaSelection.builder().build();

		return JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(java).build()).build();
	}
}
