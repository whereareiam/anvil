package me.whereareiam.anvil.platform.planning;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.environment.provisioning.workspace.directory.WorkspaceFiles;
import me.whereareiam.anvil.environment.provisioning.workspace.directory.WorkspaceProcessFiles;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformInstaller;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.PlatformRequest;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPlatformPlannerTest {
	private static final URL VERSION_DATA = DefaultPlatformPlannerTest.class.getResource("test-versions.toml");

	@TempDir
	Path directory;

	@Test
	void resolvesNamedInputsDuringPlanningAndDefersProvisioningUntilEndpointsExist() throws Exception {
		Path jar = Files.writeString(directory.resolve("server.jar"), "fixture");
		List<String> calls = new ArrayList<>();
		List<PlatformContext> contexts = new ArrayList<>();
		PlatformProvider provider = new PlatformProvider() {
			@Override
			public @NotNull String id() {
				return "test";
			}

			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() {
				return MinecraftServer.class;
			}

			@Override
			public void validateDistribution(@NotNull MinecraftProcess process) {
				assertEquals(jar, process.distribution().localJar());
				calls.add("validate");
			}

			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				calls.add("resolve");
				contexts.add(context);
				return ResolvedDistribution.builder().jar(jar).description("test").build();
			}

			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				calls.add("configure");
				contexts.add(context);
			}

			@Override
			public @NotNull Pattern readinessPattern() {
				return Pattern.compile("READY");
			}

			@Override
			public @NotNull URL versionData() {
				return VERSION_DATA;
			}
		};
		PlatformArtifactSource artifacts = new PlatformArtifactSource() {
			@Override
			public @NotNull Path install(@NotNull Path destination, @NotNull PlatformInstaller installer) {
				throw new AssertionError("No installation expected");
			}

			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String expectedSha256) {
				throw new AssertionError("Planning must not download artifacts");
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				throw new AssertionError("Planning must not download metadata");
			}
		};
		EngineOptions options = EngineOptions.builder().cacheDirectory(directory.resolve("cache"))
				.eulaAccepted(true).artifact("server", jar).build();
		DefaultPlatformPlanner planner = new DefaultPlatformPlanner(options, Map.of("test", provider), artifacts,
				entrypoint -> { throw new AssertionError("No agent is requested"); }, source -> OptionalInt.empty());
		MinecraftServer server = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.artifact("server")).minecraftVersion("1.21.11").build();
		AnvilScenario scenario = AnvilScenario.builder().name("test").entrypoint("server").server(server).build();

		PlatformPlan plan = planner.plan(scenario);

		assertEquals(List.of("validate"), calls);
		assertTrue(server.distribution().isArtifact(), "Planning must preserve the caller's declarations");
		ProcessPlan process = plan.processes().get("server");
		assertEquals(jar, process.declaration().distribution().localJar());
		PlatformRequest request = PlatformRequest.builder().scenario(plan.scenario())
				.workDirectory(directory.resolve("work/server")).workspaceGroupDirectory(directory.resolve("work"))
				.files(new WorkspaceProcessFiles(directory.resolve("work/server"), new WorkspaceFiles()))
				.cacheDirectory(directory.resolve("cache"))
				.bindAddress("0.0.0.0").port(25565)
				.processAddress("server", InetSocketAddress.createUnresolved("server-container", 25565)).build();

		assertEquals(jar, planner.resolve(process, request));
		planner.configure(process, request);

		assertEquals(List.of("validate", "resolve", "configure"), calls);
		for (PlatformContext context : contexts) {
			assertSame(artifacts, context.artifactSource());
			assertSame(process.forwarding(), context.forwarding());
			assertSame(plan.scenario(), context.scenario());
			assertEquals(request.processAddresses(), context.processAddresses());
			assertEquals(request.bindAddress(), context.bindAddress());
			assertEquals(request.port(), context.port());
			assertEquals(request.workDirectory(), context.workDirectory());
			assertEquals(request.workspaceGroupDirectory(), context.workspaceGroupDirectory());
			assertTrue(context.eulaAccepted());
		}
	}
	@Test
	void resolvesJavaPrecedenceAndProcessTopologyBeforeExecution() {
		var engineJava = JavaRequirement.builder().featureVersion(21).build();
		var scenarioJava = JavaRequirement.builder().featureVersion(25).build();
		var processJava = JavaRequirement.builder().featureVersion(17).build();
		var engineSource = JavaSource.home(directory.resolve("engine-java"));
		var scenarioSource = JavaSource.home(directory.resolve("scenario-java"));
		var processSource = JavaSource.home(directory.resolve("process-java"));
		var options = EngineOptions.builder()
				.javaSelection(JavaSelection.builder().requirement(engineJava).source(engineSource).build())
				.build();
		var planner = policyPlanner(options);
		var inherited = MinecraftServer.builder().name("inherited").platform("test")
				.distribution(Distribution.remote("1.21.11", "1")).build();
		var overridden = inherited.toBuilder()
				.name("overridden")
				.javaSelection(JavaSelection.builder().requirement(processJava).source(processSource).build())
				.build();
		var proxy = MinecraftProxy.builder().name("proxy").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.server("inherited").server("overridden").defaultServer("inherited").build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("proxy")
				.server(inherited).server(overridden).proxy(proxy)
				.networkPolicy(NetworkPolicy.builder().backendNetworkExposure(NetworkExposure.PRIVATE).build()).build();

		var enginePlan = planner.plan(scenario).processes();
		assertEquals(engineJava, enginePlan.get("inherited").javaSelection().requirement());
		assertSame(engineSource, enginePlan.get("inherited").javaSelection().source());
		var scenarioPlan = planner.plan(scenario.toBuilder()
						.javaSelection(JavaSelection.builder().requirement(scenarioJava).source(scenarioSource).build())
						.build())
				.processes();
		assertEquals(scenarioJava, scenarioPlan.get("inherited").javaSelection().requirement());
		assertSame(scenarioSource, scenarioPlan.get("inherited").javaSelection().source());
		assertEquals(processJava, scenarioPlan.get("overridden").javaSelection().requirement());
		assertSame(processSource, scenarioPlan.get("overridden").javaSelection().source());
		assertTrue(scenarioPlan.get("proxy").proxy());
		assertTrue(scenarioPlan.get("proxy").publishGame());
		assertEquals(List.of("inherited", "overridden"), scenarioPlan.get("proxy").dependencies());
		assertFalse(scenarioPlan.get("inherited").proxy());
		assertFalse(scenarioPlan.get("inherited").publishGame());
		assertTrue(scenarioPlan.get("inherited").dependencies().isEmpty());
		assertNull(inherited.javaSelection().requirement(), "Planning must preserve the caller's declarations");
		assertNull(inherited.javaSelection().source());

		var direct = scenario.toBuilder().clearProxies().entrypoint("inherited")
				.networkPolicy(NetworkPolicy.builder().build()).build();
		var directPlan = policyPlanner(EngineOptions.builder().build()).plan(direct).processes().get("inherited");
		assertTrue(directPlan.publishGame());
		assertNull(directPlan.javaSelection().source(), "Execution may select its own source when none is declared");
	}

	@Test
	void completesSourceOnlyEngineSelectionAndPreservesIndependentOverrides() {
		var engineSource = JavaSource.home(directory.resolve("engine-java"));
		var options = EngineOptions.builder()
				.javaSelection(JavaSelection.builder().source(engineSource).build())
				.build();
		var process = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1")).build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("server").server(process).build();
		var planner = policyPlanner(options);

		var inherited = planner.plan(scenario).processes().get("server").javaSelection();
		assertNotNull(inherited.requirement());
		assertEquals(21, inherited.requirement().featureVersion(), "Planning selects the platform's preferred LTS");
		assertSame(engineSource, inherited.source());

		var scenarioRequirement = JavaRequirement.builder().featureVersion(25).build();
		var processSource = JavaSource.home(directory.resolve("process-java"));
		var configured = scenario.toBuilder()
				.javaSelection(JavaSelection.builder().requirement(scenarioRequirement).build())
				.clearServers()
				.server(process.toBuilder().javaSelection(JavaSelection.builder().source(processSource).build()).build())
				.build();
		var effective = planner.plan(configured).processes().get("server").javaSelection();
		assertEquals(scenarioRequirement, effective.requirement());
		assertSame(processSource, effective.source());
		assertNull(options.javaSelection().requirement());
	}

	@Test
	void refusesASessionServerOnAPlatformThatCannotBeRedirected() {
		var process = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.sessionServer(URI.create("http://127.0.0.1:25580/session/minecraft"))
				.build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("server").server(process).build();

		PlatformException refused = assertThrows(PlatformException.class,
				() -> policyPlanner(EngineOptions.builder().build()).plan(scenario));
		assertTrue(refused.getMessage().contains("'test' cannot verify logins against another session server"),
				refused.getMessage());
	}

	private DefaultPlatformPlanner policyPlanner(EngineOptions options) {
		PlatformProvider provider = new PlatformProvider() {
			@Override
			public @NotNull String id() { return "test"; }

			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() { return MinecraftProcess.class; }

			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				throw new AssertionError("Planning must not resolve distributions");
			}

			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				throw new AssertionError("Planning must not configure processes");
			}

			@Override
			public @NotNull Pattern readinessPattern() { return Pattern.compile("READY"); }

			@Override
			public @NotNull URL versionData() { return VERSION_DATA; }
		};
		PlatformArtifactSource artifacts = new PlatformArtifactSource() {
			@Override
			public @NotNull Path install(@NotNull Path destination, @NotNull PlatformInstaller installer) {
				throw new AssertionError("No installation expected");
			}

			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String checksum) {
				throw new AssertionError("Planning must not download artifacts");
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				throw new AssertionError("Planning must not download metadata");
			}
		};
		return new DefaultPlatformPlanner(options, Map.of("test", provider), artifacts,
				entrypoint -> { throw new AssertionError("No agent is requested"); }, source -> OptionalInt.empty());
	}

}
