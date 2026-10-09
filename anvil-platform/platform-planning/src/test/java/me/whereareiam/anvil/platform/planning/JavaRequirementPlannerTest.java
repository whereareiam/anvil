package me.whereareiam.anvil.platform.planning;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.api.type.SupportPolicy;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plans Java against data shaped like the built-in Paper, Spigot, Velocity and BungeeCord data,
 * read from test resources the way providers expose theirs.
 */
class JavaRequirementPlannerTest {
	private static final String BYPASS = "-DPaper.IgnoreJavaVersion=true";
	private static final Map<String, PlatformProvider> PROVIDERS = Map.of(
			"paper", new TablePlatform("paper", MinecraftServer.class, true, true),
			"spigot", new TablePlatform("spigot", MinecraftServer.class, true, true),
			"velocity", new TablePlatform("velocity", MinecraftProxy.class, true, true),
			"bungeecord", new TablePlatform("bungeecord", MinecraftProxy.class, false, true)
	);
	private static final Map<String, PlatformProvider> UNUSUAL_PROVIDERS = Map.of(
			"legacy", new TablePlatform("legacy", MinecraftServer.class, true, true),
			"legacy-proxy", new TablePlatform("legacy-proxy", MinecraftProxy.class, false, false),
			"undeclared", new TablePlatform("undeclared", MinecraftServer.class, true, true),
			"missing", new TablePlatform("missing", MinecraftServer.class, true, true)
	);

	@ParameterizedTest(name = "{0} {1} requesting Java [{2}] under {3} runs Java {4} ({5})")
	@CsvSource({
			"paper,      1.16.5,  ,   STRICT,  11, VERIFIED,   false",
			"paper,      1.16.5,  17, STRICT,  17, VERIFIED,   true",
			"paper,      1.16.5,  21, LENIENT, 21, UNTESTED,   true",
			"paper,      1.17.1,  ,   STRICT,  17, VERIFIED,   false",
			"paper,      1.17.1,  21, LENIENT, 21, UNTESTED,   true",
			"paper,      1.18.2,  21, STRICT,  21, COMPATIBLE, false",
			"paper,      1.20.4,  ,   STRICT,  17, COMPATIBLE, false",
			"paper,      1.20.6,  ,   STRICT,  21, VERIFIED,   false",
			"paper,      1.21.11, 25, STRICT,  25, COMPATIBLE, false",
			"paper,      26.1,    ,   STRICT,  25, COMPATIBLE, false",
			"paper,      26.1.2,  ,   STRICT,  25, VERIFIED,   false",
			"paper,      26.2,    ,   LENIENT, 25, UNTESTED,   false",
			"spigot,     1.16.5,  ,   STRICT,  11, VERIFIED,   false",
			"spigot,     1.19.4,  ,   STRICT,  17, VERIFIED,   false",
			"spigot,     1.21.11, ,   LENIENT, 21, UNTESTED,   false",
			"velocity,   3.4.0,   ,   STRICT,  21, COMPATIBLE, false",
			"velocity,   3.4.0,   25, STRICT,  25, COMPATIBLE, false",
			"velocity,   3.5.1,   ,   STRICT,  21, VERIFIED,   false",
			"velocity,   4.0.0,   ,   STRICT,  25, COMPATIBLE, false",
			"bungeecord, ,        ,   STRICT,  21, COMPATIBLE, false",
			"bungeecord, ,        25, STRICT,  25, COMPATIBLE, false"
	})
	void selectsAnExactLtsVersionAndAssessesTheProcess(
			String platform,
			String version,
			Integer requested,
			SupportPolicy policy,
			int java,
			SupportLevel level,
			boolean bypass
	) {
		var planned = plan(PROVIDERS, process(platform, version, requested), policy, new ByteArrayOutputStream());

		assertEquals(java, planned.selection().getRequirement().getFeatureVersion());
		assertEquals(level, planned.support());
		assertEquals(bypass ? List.of(BYPASS) : List.of(), planned.jvmArguments());
	}

	@Test
	void spigotOneSixteenOnJavaSeventeenIsRefusedWithTheDocumentedAdvice() {
		var failure = refuse(PROVIDERS, process("spigot", "1.16.5", 17), SupportPolicy.LENIENT);

		assertEquals("Process 'spigot' requests Java 17 (requested by process 'spigot'), but spigot 1.16.5 refuses "
				+ "Java above 16 and has no bypass. Remove the Java requirement to use Java 11 (default), or use "
				+ "'paper', which runs Minecraft 1.16.5 on Java 17 with -DPaper.IgnoreJavaVersion=true.", failure.getMessage());
	}

	@Test
	void suggestsOnlyPlatformsTheSupportPolicyAccepts() {
		var strict = refuse(PROVIDERS, process("spigot", "1.16.5", 21), SupportPolicy.STRICT);
		assertEquals("Process 'spigot' requests Java 21 (requested by process 'spigot'), but spigot 1.16.5 refuses "
				+ "Java above 16 and has no bypass. Remove the Java requirement to use Java 11 (default).", strict.getMessage());

		var lenient = refuse(PROVIDERS, process("spigot", "1.16.5", 21), SupportPolicy.LENIENT);
		assertTrue(lenient.getMessage().endsWith(", or use 'paper', which runs Minecraft 1.16.5 on Java 21 with "
				+ "-DPaper.IgnoreJavaVersion=true."), lenient.getMessage());

		var verified = refuse(PROVIDERS, process("spigot", "1.16.5", 17), SupportPolicy.STRICT);
		assertTrue(verified.getMessage().contains("or use 'paper', which runs Minecraft 1.16.5 on Java 17"),
				verified.getMessage());
	}

	@ParameterizedTest(name = "{0} {1} requesting Java {2} under {3} is refused")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
			"paper      | 1.16.5  | 21 | STRICT  | is UNTESTED: Java 21 is above the maximum Java 16 for this version and runs with -DPaper.IgnoreJavaVersion=true; verified Java for paper 1.16.5: 11, 17. The STRICT support policy refuses UNTESTED processes; use Java 11 (default) or a verified Java version (11, 17), or run under the LENIENT support policy.",
			"paper      | 1.16.5  | 16 | LENIENT | requests Java 16 (requested by process 'paper'), which is not an LTS release. Anvil runs processes on LTS releases only: 11, 17, 21, 25, then every fourth release. paper 1.16.5 runs Java 11 to 16 (default 11); newer Java runs only with -DPaper.IgnoreJavaVersion=true.",
			"paper      | 1.16.5  | 8  | LENIENT | requests Java 8 (requested by process 'paper'), which is not an LTS release",
			"paper      | 1.21.11 | 17 | LENIENT | but paper 1.21.11 requires Java 21 or newer",
			"paper      | 1.12.2  |    | LENIENT | paper 1.12.2 is UNSUPPORTED; it is older than the first version Anvil supports for this platform (1.16.5)",
			"paper      | 26.2    |    | STRICT  | paper 26.2 is not a version Anvil knows (newest known: 26.1.2)",
			"spigot     | 1.20.6  | 25 | LENIENT | spigot 1.20.6 refuses Java above 22 and has no bypass",
			"spigot     | 1.17.1  | 21 | LENIENT | Remove the Java requirement to use Java 17 (default), or use 'paper', which runs Minecraft 1.17.1 on Java 21 with -DPaper.IgnoreJavaVersion=true",
			"velocity   | 3.4.0   | 17 | LENIENT | requests Java 17 (requested by process 'velocity'), but the Anvil agent installed into velocity requires Java 21 or newer. velocity 3.4 runs Java 17 or newer, and its Anvil agent requires Java 21 or newer (default 21).",
			"bungeecord |         | 17 | LENIENT | but the Anvil agent installed into bungeecord requires Java 21 or newer. bungeecord runs Java 17 or newer, and its Anvil agent requires Java 21 or newer (default 21)."
	})
	void refusesRequestsBeforeAnythingStarts(String platform, String version, Integer requested, SupportPolicy policy, String message) {
		var failure = refuse(PROVIDERS, process(platform, version, requested), policy);

		assertTrue(failure.getMessage().contains(message), failure.getMessage());
	}

	@Test
	void refusesADefaultTheAgentCannotRunAndAllowsAnExplicitBypass() {
		var failure = refuse(UNUSUAL_PROVIDERS, process("legacy", "1.16.5", null), SupportPolicy.LENIENT);
		assertEquals("Process 'legacy': legacy 1.16.5 runs at most Java 16, but the Anvil agent installed into legacy "
				+ "requires Java 17 or newer, so no LTS release runs both by default. Request Java 17 explicitly to run it "
				+ "above the maximum with -DLegacy.IgnoreJavaVersion=true.", failure.getMessage());

		var planned = plan(UNUSUAL_PROVIDERS, process("legacy", "1.16.5", 17), SupportPolicy.LENIENT, new ByteArrayOutputStream());
		assertEquals(17, planned.selection().getRequirement().getFeatureVersion());
		assertEquals(SupportLevel.UNTESTED, planned.support());
		assertEquals(List.of("-DLegacy.IgnoreJavaVersion=true"), planned.jvmArguments());

		var belowAgent = refuse(UNUSUAL_PROVIDERS, process("legacy", "1.16.5", 11), SupportPolicy.LENIENT);
		assertTrue(belowAgent.getMessage().contains("legacy 1.16.5 runs Java 11 to 16, and its Anvil agent requires Java 17 "
				+ "or newer; newer Java runs only with -DLegacy.IgnoreJavaVersion=true."), belowAgent.getMessage());
	}

	@Test
	void refusesAnUnversionedBypassUnderTheStrictPolicyWithoutFailingOnTheMissingVersion() {
		var failure = refuse(UNUSUAL_PROVIDERS, process("legacy-proxy", null, 17), SupportPolicy.STRICT);

		assertEquals("Process 'legacy-proxy': legacy-proxy on Java 17 is UNTESTED: the legacy-proxy distribution carries no "
				+ "version Anvil can assess; Java 17 is above the maximum Java 16 for this version and runs with "
				+ "-DProxy.IgnoreJavaVersion=true; no Java version is verified for legacy-proxy. The STRICT support policy "
				+ "refuses UNTESTED processes; use Java 11 (default), or run under the LENIENT support policy.", failure.getMessage());
	}

	@Test
	void refusesProvidersWhoseVersionDataIsMissingOrOmitsTheirAgent() {
		var undeclared = refuse(UNUSUAL_PROVIDERS, process("undeclared", "1.16.5", null), SupportPolicy.LENIENT);
		assertEquals("Process 'undeclared': platform 'undeclared' installs an Anvil agent, but its version data declares "
				+ "no [agent] minimumJava.", undeclared.getMessage());

		var missing = refuse(UNUSUAL_PROVIDERS, process("missing", "1.16.5", null), SupportPolicy.LENIENT);
		assertEquals("Process 'missing': Platform 'missing' supplies no version data", missing.getMessage());
	}

	@Test
	void usesTheNewestRowForAProxyJarWithoutVersion() {
		var local = MinecraftProxy.builder().name("velocity").platform("velocity")
				.distribution(Distribution.local(Path.of("velocity.jar"))).server("lobby").defaultServer("lobby").build();
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		var planned = plan(PROVIDERS, local, SupportPolicy.STRICT, output);
		assertEquals(25, planned.selection().getRequirement().getFeatureVersion());
		assertEquals(SupportLevel.COMPATIBLE, planned.support());
		assertTrue(output.toString(StandardCharsets.UTF_8).startsWith("[Anvil] Info: process 'velocity' runs velocity on "
				+ "Java 25 (COMPATIBLE): the velocity distribution carries no version Anvil can assess"), output.toString());
	}

	@Test
	void reportsBypassAndUntestedProcessesAndStaysQuietForVerifiedOnes() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		plan(PROVIDERS, process("paper", "1.16.5", 17), SupportPolicy.STRICT, output);
		plan(PROVIDERS, process("paper", "1.16.5", 21), SupportPolicy.LENIENT, output);
		plan(PROVIDERS, process("paper", "1.18.2", 21), SupportPolicy.LENIENT, output);
		plan(PROVIDERS, process("paper", "1.20.6", null), SupportPolicy.LENIENT, output);

		List<String> lines = output.toString(StandardCharsets.UTF_8).lines().toList();
		assertEquals(3, lines.size(), lines.toString());
		assertEquals("[Anvil] Info: process 'paper' runs paper 1.16.5 on Java 17 (VERIFIED): Java 17 is above the maximum "
				+ "Java 16 for this version and runs with -DPaper.IgnoreJavaVersion=true; Anvil's live matrix verifies "
				+ "this combination although the platform does not support it.", lines.get(0));
		assertTrue(lines.get(1).startsWith("[Anvil] Warning: process 'paper' runs paper 1.16.5 on Java 21 (UNTESTED)"), lines.get(1));
		assertTrue(lines.get(1).endsWith("The STRICT support policy refuses this process."), lines.get(1));
		assertEquals("[Anvil] Info: process 'paper' runs paper 1.18.2 on Java 21 (COMPATIBLE): Java 21 is inside the "
				+ "accepted range (Java 17 or newer) but verified Java for paper 1.18.2: 17.", lines.get(2));
	}

	@Test
	void reportsTheAgentRaisedRangeForAKnownProxyRelease() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		plan(PROVIDERS, process("velocity", "3.4.0", null), SupportPolicy.STRICT, output);
		assertEquals("[Anvil] Info: process 'velocity' runs velocity 3.4 on Java 21 (COMPATIBLE): velocity 3.4 is a "
				+ "known version but is not verified; Java 21 is inside the accepted range (Java 21 or newer) but no Java "
				+ "version is verified for velocity 3.4.", output.toString(StandardCharsets.UTF_8).strip());
	}

	@Test
	void namesTheProcessWhenAnInheritedRequestDoesNotFitIt() {
		var options = EngineOptions.builder()
				.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(21).build()).build())
				.build();
		var planner = new JavaRequirementPlanner(options, PROVIDERS, new PrintStream(new ByteArrayOutputStream()));
		var legacy = server("spigot", "1.16.5", null);
		var modern = server("paper", "1.21.11", null);
		var scenario = scenario(legacy);

		assertEquals(21, planner.plan(scenario(modern), modern).selection().getRequirement().getFeatureVersion());
		var failure = assertThrows(ScenarioValidationException.class, () -> planner.plan(scenario, legacy));
		assertTrue(failure.getMessage().startsWith("Process 'spigot' requests Java 21 (requested by the engine Java selection)"),
				failure.getMessage());
		assertTrue(failure.getMessage().endsWith("declare a JavaRequirement on process 'spigot' to override it."),
				failure.getMessage());
		var scenarioFailure = assertThrows(ScenarioValidationException.class, () -> planner.plan(scenario.toBuilder()
				.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(17).build()).build())
				.build(), legacy));
		assertTrue(scenarioFailure.getMessage().contains("(requested by scenario 'java')"), scenarioFailure.getMessage());
	}

	@Test
	void derivesTheFeatureFromAnExactReleaseAndKeepsOtherRequirementMembers() {
		var release = JavaRequirement.builder().release("17.0.12+7").distribution("graalvm-community").build();
		var planned = plan(PROVIDERS, server("paper", "1.18.2", null).toBuilder()
				.javaSelection(JavaSelection.builder().requirement(release).build()).build(), SupportPolicy.STRICT,
				new ByteArrayOutputStream());

		assertEquals(release.toBuilder().featureVersion(17).build(), planned.selection().getRequirement());
		var distributionOnly = plan(PROVIDERS, server("paper", "1.18.2", null).toBuilder()
				.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().distribution("temurin").build()).build())
				.build(), SupportPolicy.STRICT, new ByteArrayOutputStream());
		assertEquals(17, distributionOnly.selection().getRequirement().getFeatureVersion());
		assertEquals("temurin", distributionOnly.selection().getRequirement().getDistribution());
		var mismatch = refuse(PROVIDERS, server("paper", "1.18.2", null).toBuilder()
				.javaSelection(JavaSelection.builder().requirement(release.toBuilder().featureVersion(21).build()).build())
				.build(), SupportPolicy.LENIENT);
		assertTrue(mismatch.getMessage().contains("but that release belongs to Java 17"), mismatch.getMessage());
	}

	@Test
	void appliesTheScenarioPolicyBeforeTheEnginePolicy() {
		var process = process("paper", "1.16.5", 21);
		var strictEngine = new JavaRequirementPlanner(EngineOptions.builder().supportPolicy(SupportPolicy.STRICT).build(),
				PROVIDERS, new PrintStream(new ByteArrayOutputStream()));

		assertThrows(ScenarioValidationException.class, () -> strictEngine.plan(scenario(process), process));
		assertEquals(SupportLevel.UNTESTED, strictEngine.plan(scenario(process).toBuilder()
				.supportPolicy(SupportPolicy.LENIENT).build(), process).support());
	}

	@Test
	void placesTheBypassBeforePlatformDefaultsInTheProcessPlan() {
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
		var planner = new DefaultPlatformPlanner(EngineOptions.builder().consoleColors(true).build(), PROVIDERS, artifacts,
				agent -> Path.of("agent.jar"));
		var server = server("paper", "1.16.5", 17).toBuilder().jvmArgument("-Ddeclared=true").build();

		ProcessPlan plan = planner.plan(scenario(server)).getProcesses().get("paper");
		assertEquals(List.of(BYPASS, "-Dplatform.colors=true"), plan.getJvmArguments());
		assertEquals(17, plan.getJavaSelection().getRequirement().getFeatureVersion());
	}

	private static JavaRequirementPlanner.PlannedJava plan(
			Map<String, PlatformProvider> providers,
			MinecraftProcess process,
			SupportPolicy policy,
			ByteArrayOutputStream output
	) {
		var planner = new JavaRequirementPlanner(EngineOptions.builder().supportPolicy(policy).build(), providers,
				new PrintStream(output, true, StandardCharsets.UTF_8));
		return planner.plan(scenario(process), process);
	}

	private static ScenarioValidationException refuse(Map<String, PlatformProvider> providers, MinecraftProcess process, SupportPolicy policy) {
		var planner = new JavaRequirementPlanner(EngineOptions.builder().supportPolicy(policy).build(), providers,
				new PrintStream(new ByteArrayOutputStream()));
		return assertThrows(ScenarioValidationException.class, () -> planner.plan(scenario(process), process));
	}

	private static AnvilScenario scenario(MinecraftProcess process) {
		var scenario = AnvilScenario.builder().name("java").entrypoint(process.getName());
		if (process instanceof MinecraftServer server) return scenario.server(server).build();

		return scenario.proxy((MinecraftProxy) process).build();
	}

	private static MinecraftProcess process(String platform, @Nullable String version, @Nullable Integer requested) {
		if (MinecraftServer.class.equals(PROVIDERS.getOrDefault(platform, UNUSUAL_PROVIDERS.get(platform)).configurationType()))
			return server(platform, version, requested);

		Distribution distribution = Distribution.remote(version == null ? "Unversioned" : version, "1");
		return MinecraftProxy.builder().name(platform).platform(platform).server("lobby").defaultServer("lobby")
				.distribution(distribution).javaSelection(selection(requested)).build();
	}

	private static MinecraftServer server(String platform, String version, @Nullable Integer requested) {
		return MinecraftServer.builder().name(platform).platform(platform)
				.distribution(Distribution.remote(version, "1")).javaSelection(selection(requested)).build();
	}

	private static JavaSelection selection(@Nullable Integer requested) {
		if (requested == null) return JavaSelection.builder().build();

		return JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(requested).build()).build();
	}

	/**
	 * A platform whose version data is the {@code <id>-versions.toml} test resource beside this test.
	 *
	 * @param id platform identifier and resource prefix
	 * @param type process declaration type the platform accepts
	 * @param versioned whether a proxy's distribution version keys its data
	 * @param agent whether the platform installs an agent
	 */
	private record TablePlatform(
			String id,
			Class<? extends MinecraftProcess> type,
			boolean versioned,
			boolean agent
	) implements PlatformProvider {
		@Override
		public @NotNull Class<? extends MinecraftProcess> configurationType() {
			return type;
		}

		@Override
		public @NotNull URL versionData() {
			return JavaRequirementPlannerTest.class.getResource(id + "-versions.toml");
		}

		@Override
		public @Nullable MinecraftVersion platformVersion(@NotNull MinecraftProcess process) {
			if (process instanceof MinecraftServer) return PlatformProvider.super.platformVersion(process);
			if (!versioned) return null;

			String version = process.getDistribution().getVersion();
			return version == null ? null : MinecraftVersion.parse(version);
		}

		@Override
		public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			throw new AssertionError("Planning must not resolve distributions");
		}

		@Override
		public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			throw new AssertionError("Planning must not configure processes");
		}

		@Override
		public @NotNull Pattern readinessPattern() {
			return Pattern.compile("READY");
		}

		@Override
		public @NotNull List<String> jvmArguments(@NotNull MinecraftProcess process, boolean consoleColors) {
			return consoleColors ? List.of("-Dplatform.colors=true") : List.of();
		}

		@Override
		public @Nullable PlatformAgentDescriptor platformAgent() {
			return agent ? PlatformAgentDescriptor.builder().entrypointClassName("test.Agent").build() : null;
		}
	}
}
