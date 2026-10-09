package me.whereareiam.anvil.testkit.tests.server.scenario;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.platform.planning.version.PlatformVersionsReader;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the verified data of the bundled platforms and of MCProtocolLib equal to the live matrix these
 * scenarios define: a combination is VERIFIED exactly when a matrix scenario runs it. It also keeps one direct
 * Paper scenario, which player capability tests run their full journey on, for every MCProtocolLib release. The
 * test plans the scenarios without starting any process.
 */
class CompatibilityScenarioFactoryTest {
	private static final Map<String, PlatformProvider> PLATFORMS = ServiceLoader.load(PlatformProvider.class).stream()
			.map(ServiceLoader.Provider::get)
			.collect(Collectors.toMap(PlatformProvider::id, Function.identity()));

	@TempDir
	Path directory;

	@Test
	void platformVerifiedDataListsExactlyTheJavaEachMatrixScenarioRunsPerPlatformVersion() {
		Map<String, Map<MinecraftVersion, Set<Integer>>> matrix = new TreeMap<>();
		for (AnvilScenario scenario : CompatibilityScenarioFactory.scenarios())
			for (ProcessPlan plan : planner().plan(scenario).getProcesses().values()) {
				MinecraftProcess process = plan.getDeclaration();
				Optional<MinecraftVersion> version = version(process);
				if (version.isEmpty()) continue;

				matrix.computeIfAbsent(process.getPlatform(), platform -> new TreeMap<>())
						.computeIfAbsent(version.get(), ignored -> new TreeSet<>())
						.add(plan.getJavaSelection().getRequirement().getFeatureVersion());
			}

		PLATFORMS.forEach((id, provider) -> assertEquals(matrix.getOrDefault(id, Map.of()),
				new TreeMap<>(new PlatformVersionsReader().read(id, provider.versionData()).getVerifiedJavaVersions()),
				"Verified data of " + id));
	}

	@Test
	void mcprotocolVerifiedVersionsAreExactlyTheMatrixServerVersions() {
		Set<MinecraftVersion> servers = CompatibilityScenarioFactory.scenarios().stream()
				.flatMap(scenario -> scenario.getServers().stream())
				.map(MinecraftServer::nativeVersion)
				.collect(Collectors.toCollection(TreeSet::new));

		Set<MinecraftVersion> verified = mcprotocolReleases().stream()
				.map(ProtocolRelease::getVerifiedVersions)
				.flatMap(Set::stream)
				.collect(Collectors.toCollection(TreeSet::new));

		assertEquals(servers, verified);
	}

	@Test
	void paperReleaseScenariosRunEveryMcprotocolReleaseDirectOnPaperWithinTheMatrix() {
		List<AnvilScenario> releases = CompatibilityScenarioFactory.paperReleaseScenarios();
		Set<String> matrix = CompatibilityScenarioFactory.scenarios().stream()
				.map(AnvilScenario::getName)
				.collect(Collectors.toSet());
		for (AnvilScenario scenario : releases) {
			assertTrue(matrix.contains(scenario.getName()), scenario.getName() + " is not a matrix scenario");
			assertEquals(List.of(), scenario.getProxies(), scenario.getName() + " proxies");
			assertEquals(List.of(Platforms.PAPER), scenario.getServers().stream().map(MinecraftServer::getPlatform).toList(),
					scenario.getName() + " server platforms");
		}

		List<MinecraftVersion> versions = releases.stream()
				.map(scenario -> scenario.getServers().getFirst().nativeVersion())
				.sorted()
				.toList();
		List<MinecraftVersion> releaseKeys = mcprotocolReleases().stream()
				.map(ProtocolRelease::version)
				.sorted()
				.toList();

		assertEquals(releaseKeys, versions, "Minecraft versions of the direct Paper scenarios");
	}

	private List<ProtocolRelease> mcprotocolReleases() {
		ProtocolLibraryProvider mcprotocol = ServiceLoader.load(ProtocolLibraryProvider.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(library -> library.id().equals("mcprotocol"))
				.findFirst().orElseThrow();
		ProtocolLibraryContext context = ProtocolLibraryContext.builder()
				.cacheDirectory(directory)
				.accountsDirectory(directory.resolve("accounts"))
				.artifacts((artifact, destination, sha256) -> {
					throw new AssertionError("Reading release data must not download artifacts");
				})
				.build();

		return mcprotocol.releases(context);
	}

	private static Optional<MinecraftVersion> version(MinecraftProcess process) {
		String version = process.getDistribution().getVersion();
		if (version == null) return Optional.empty();

		try {
			return Optional.of(MinecraftVersion.parse(version));
		} catch (IllegalArgumentException unversioned) {
			// BungeeCord builds carry no release version, so its data verifies none.
			return Optional.empty();
		}
	}

	private static DefaultPlatformPlanner planner() {
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

		return new DefaultPlatformPlanner(EngineOptions.builder().eulaAccepted(true).build(), PLATFORMS, artifacts,
				agent -> Path.of("agent.jar"));
	}
}
