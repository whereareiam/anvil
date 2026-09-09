package me.whereareiam.anvil.platform.planning;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPlatformPlannerTest {
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
				assertEquals(jar, process.getDistribution().getLocalJar());
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
			public int minimumJavaVersion(@NotNull MinecraftProcess process) {
				return 21;
			}
		};
		PlatformArtifactSource artifacts = new PlatformArtifactSource() {
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
				entrypoint -> { throw new AssertionError("No agent is requested"); });
		MinecraftServer server = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.artifact("server")).minecraftVersion("1.21.11").build();
		AnvilScenario scenario = AnvilScenario.builder().name("test").entrypoint("server").server(server).build();

		PlatformPlan plan = planner.plan(scenario);

		assertEquals(List.of("validate"), calls);
		assertTrue(server.getDistribution().isArtifact(), "Planning must preserve the caller's declarations");
		ProcessPlan process = plan.getProcesses().get("server");
		assertEquals(jar, process.getDeclaration().getDistribution().getLocalJar());
		PlatformRequest request = PlatformRequest.builder().scenario(plan.getScenario())
				.workDirectory(directory.resolve("work/server")).workspaceGroupDirectory(directory.resolve("work"))
				.bindAddress("0.0.0.0").port(25565)
				.processAddress("server", InetSocketAddress.createUnresolved("server-container", 25565)).build();

		assertEquals(jar, planner.resolve(process, request));
		planner.configure(process, request);

		assertEquals(List.of("validate", "resolve", "configure"), calls);
		for (PlatformContext context : contexts) {
			assertSame(artifacts, context.getArtifactSource());
			assertSame(process.getForwarding(), context.getForwarding());
			assertSame(plan.getScenario(), context.getScenario());
			assertEquals(request.getProcessAddresses(), context.getProcessAddresses());
			assertEquals(request.getBindAddress(), context.getBindAddress());
			assertEquals(request.getPort(), context.getPort());
			assertEquals(request.getWorkDirectory(), context.getWorkDirectory());
			assertEquals(request.getWorkspaceGroupDirectory(), context.getWorkspaceGroupDirectory());
			assertTrue(context.isEulaAccepted());
		}
	}
	@Test
	void resolvesJavaPrecedenceAndProcessTopologyBeforeExecution() {
		var engineJava = JavaRequirement.builder().featureVersion(21).build();
		var scenarioJava = JavaRequirement.builder().featureVersion(25).build();
		var processJava = JavaRequirement.builder().featureVersion(26).build();
		var engineSource = JavaSource.home(directory.resolve("engine-java"));
		var scenarioSource = JavaSource.home(directory.resolve("scenario-java"));
		var processSource = JavaSource.home(directory.resolve("process-java"));
		var options = EngineOptions.builder().javaRequirement(engineJava).javaSource(engineSource).build();
		var planner = policyPlanner(options);
		var inherited = MinecraftServer.builder().name("inherited").platform("test")
				.distribution(Distribution.remote("1.21.11", "1")).build();
		var overridden = inherited.toBuilder().name("overridden").javaRequirement(processJava).javaSource(processSource).build();
		var proxy = MinecraftProxy.builder().name("proxy").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.server("inherited").server("overridden").defaultServer("inherited").build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("proxy")
				.server(inherited).server(overridden).proxy(proxy)
				.networkPolicy(NetworkPolicy.builder().backendNetworkExposure(NetworkExposure.PRIVATE).build()).build();

		var enginePlan = planner.plan(scenario).getProcesses();
		assertSame(engineJava, enginePlan.get("inherited").getJavaRequirement());
		assertSame(engineSource, enginePlan.get("inherited").getJavaSource());
		var scenarioPlan = planner.plan(scenario.toBuilder().javaRequirement(scenarioJava).javaSource(scenarioSource).build()).getProcesses();
		assertSame(scenarioJava, scenarioPlan.get("inherited").getJavaRequirement());
		assertSame(scenarioSource, scenarioPlan.get("inherited").getJavaSource());
		assertSame(processJava, scenarioPlan.get("overridden").getJavaRequirement());
		assertSame(processSource, scenarioPlan.get("overridden").getJavaSource());
		assertTrue(scenarioPlan.get("proxy").isProxy());
		assertTrue(scenarioPlan.get("proxy").isPublishGame());
		assertEquals(List.of("inherited", "overridden"), scenarioPlan.get("proxy").getDependencies());
		assertFalse(scenarioPlan.get("inherited").isProxy());
		assertFalse(scenarioPlan.get("inherited").isPublishGame());
		assertTrue(scenarioPlan.get("inherited").getDependencies().isEmpty());
		assertNull(inherited.getJavaRequirement(), "Planning must preserve the caller's declarations");
		assertNull(inherited.getJavaSource());

		var direct = scenario.toBuilder().clearProxies().entrypoint("inherited")
				.networkPolicy(NetworkPolicy.builder().build()).build();
		var directPlan = policyPlanner(EngineOptions.builder().build()).plan(direct).getProcesses().get("inherited");
		assertTrue(directPlan.isPublishGame());
		assertNull(directPlan.getJavaSource(), "Execution may select its own source when none is declared");
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
			public int minimumJavaVersion(@NotNull MinecraftProcess process) { return 21; }
		};
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
		return new DefaultPlatformPlanner(options, Map.of("test", provider), artifacts,
				entrypoint -> { throw new AssertionError("No agent is requested"); });
	}

}
