package me.whereareiam.anvil.testkit.tests.runtime.provider;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.SupportPolicy;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.platform.planning.version.JavaCompatibility;
import me.whereareiam.anvil.platform.planning.version.PlatformVersions;
import me.whereareiam.anvil.platform.planning.version.PlatformVersionsReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Keeps the Java guide's table of Java versions per platform version equal to what the bundled platform providers
 * ship: one table row per Java row of every installed provider, with its versions, range, the default the planner
 * selects, and the combinations the live matrix verifies.
 */
class JavaVersionTableDocumentationTest {
	private static final String HEADER = "| Platform | Versions | Range | Default | Verified by the live matrix |";
	private static final Map<String, PlatformProvider> PROVIDERS = ServiceLoader.load(PlatformProvider.class).stream()
			.map(ServiceLoader.Provider::get)
			.collect(Collectors.toMap(PlatformProvider::id, Function.identity(), (first, second) -> first, TreeMap::new));

	@Test
	void theJavaGuideTableListsEveryProvidersJavaRows() throws IOException {
		assertFalse(PROVIDERS.isEmpty(), "No platform provider is installed");
		Map<String, List<String>> documented = documentedRows();
		Map<String, List<String>> expected = new TreeMap<>();
		PROVIDERS.forEach((id, provider) -> expected.put(id, rows(label(id, documented), provider)));

		String table = expected.values().stream().flatMap(List::stream).collect(Collectors.joining("\n"));
		assertEquals(expected, new TreeMap<>(documented), "Expected the Java guide table to list these rows:\n" + HEADER
				+ "\n|---|---|---|---|---|\n" + table);
	}

	private static List<String> rows(String label, PlatformProvider provider) {
		PlatformVersions versions = new PlatformVersionsReader().read(provider.id(), provider.versionData());
		List<JavaCompatibility> compatibilities = versions.getJavaCompatibilities();
		TreeSet<MinecraftVersion> known = new TreeSet<>(versions.getKnownVersions());
		known.addAll(versions.getVerifiedJavaVersions().keySet());
		List<String> rows = new ArrayList<>();
		for (int index = 0; index < compatibilities.size(); index++) {
			JavaCompatibility row = compatibilities.get(index);
			MinecraftVersion next = index + 1 < compatibilities.size() ? compatibilities.get(index + 1).getSince() : null;
			rows.add("| " + label + " | " + versions(row.getSince(), next, known) + " | " + range(row, versions.getAgentMinimumJava())
					+ " | " + plannedDefault(provider, representative(row.getSince(), next, known)) + " | "
					+ verified(row.getSince(), next, versions) + " |");
		}

		return rows;
	}

	private static String versions(MinecraftVersion since, MinecraftVersion next, TreeSet<MinecraftVersion> known) {
		if (since == null) return "every build";
		if (next == null) return "`" + since + "` and newer";

		MinecraftVersion last = known.lower(next);
		if (last == null || last.compareTo(since) <= 0) return "`" + since + "`";

		return "`" + since + "` to `" + last + "`";
	}

	/**
	 * Picks the version a row's default is planned for: the oldest known version the row covers, else the row's
	 * start, or no version for a row that covers every build.
	 */
	private static @Nullable MinecraftVersion representative(
			@Nullable MinecraftVersion since,
			@Nullable MinecraftVersion next,
			TreeSet<MinecraftVersion> known
	) {
		MinecraftVersion oldest = since == null ? (known.isEmpty() ? null : known.first()) : known.ceiling(since);
		if (oldest != null && (next == null || oldest.compareTo(next) < 0)) return oldest;

		return since;
	}

	private static String range(JavaCompatibility row, Integer agent) {
		String range = bounds(row);
		if (row.getMaximumBypassProperty() != null) range += "; newer with `-D" + row.getMaximumBypassProperty() + "=true`";
		if (agent != null && agent > row.getMinimum()) range += "; the Anvil agent needs " + agent;

		return range;
	}

	private static String bounds(JavaCompatibility row) {
		if (row.getMaximum() == null) return "Java " + row.getMinimum() + " or newer";
		if (row.getMaximum() == row.getMinimum()) return "Java " + row.getMinimum();

		return "Java " + row.getMinimum() + " to " + row.getMaximum();
	}

	/**
	 * Plans a process of the provider's platform without a Java requirement and returns the Java the planner
	 * selects for it.
	 */
	private static int plannedDefault(PlatformProvider provider, @Nullable MinecraftVersion version) {
		MinecraftProcess process = process(provider, "process", version);
		var scenario = AnvilScenario.builder().name("java-table").entrypoint(process.getName());
		if (process instanceof MinecraftProxy proxy) scenario.proxy(proxy).server(backend());
		if (process instanceof MinecraftServer server) scenario.server(server);

		return planner().plan(scenario.build()).getProcesses().get(process.getName())
				.getJavaSelection().getRequirement().getFeatureVersion();
	}

	private static MinecraftProcess process(PlatformProvider provider, String name, @Nullable MinecraftVersion version) {
		if (MinecraftServer.class.isAssignableFrom(provider.configurationType()))
			return MinecraftServer.builder().name(name).platform(provider.id())
					.distribution(Distribution.local(Path.of(name + ".jar")))
					.minecraftVersion(version == null ? null : version.toString())
					.build();

		Distribution distribution = version == null
				? Distribution.local(Path.of(name + ".jar"))
				: Distribution.remote(version.toString(), "1");
		return MinecraftProxy.builder().name(name).platform(provider.id()).distribution(distribution)
				.server("backend").defaultServer("backend").build();
	}

	/**
	 * A proxy needs a backend: a server of any installed server platform that accepts forwarded identities, on
	 * its newest known version.
	 */
	private static MinecraftServer backend() {
		for (PlatformProvider provider : PROVIDERS.values()) {
			if (!MinecraftServer.class.isAssignableFrom(provider.configurationType())) continue;
			if (provider.forwardingModes().equals(List.of(ForwardingMode.NONE))) continue;

			PlatformVersions versions = new PlatformVersionsReader().read(provider.id(), provider.versionData());
			return (MinecraftServer) process(provider, "backend", versions.newestKnownVersion().orElse(null));
		}

		throw new AssertionError("A proxy row needs an installed server platform for its backend");
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

		return new DefaultPlatformPlanner(EngineOptions.builder().supportPolicy(SupportPolicy.LENIENT).build(), PROVIDERS,
				artifacts, agent -> Path.of("agent.jar"));
	}

	private static String verified(MinecraftVersion since, MinecraftVersion next, PlatformVersions versions) {
		Map<Set<Integer>, List<String>> byJava = new LinkedHashMap<>();
		new TreeMap<>(versions.getVerifiedJavaVersions()).forEach((version, java) -> {
			if (since != null && version.compareTo(since) < 0) return;
			if (next != null && version.compareTo(next) >= 0) return;

			byJava.computeIfAbsent(new TreeSet<>(java), ignored -> new ArrayList<>()).add("`" + version + "`");
		});
		if (byJava.isEmpty()) return "None";

		List<String> groups = new ArrayList<>();
		byJava.forEach((java, platformVersions) -> groups.add(join(platformVersions) + " on "
				+ join(java.stream().map(String::valueOf).toList())));
		return String.join("; ", groups);
	}

	private static String join(List<String> values) {
		if (values.size() == 1) return values.getFirst();

		return String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.getLast();
	}

	/**
	 * Names a platform as the guide does, matching its label to the provider ID regardless of case, or by its ID
	 * when the guide does not list it.
	 */
	private static String label(String id, Map<String, List<String>> documented) {
		List<String> rows = documented.get(id);
		if (rows == null || rows.isEmpty()) return id;

		return cells(rows.getFirst()).getFirst();
	}

	/**
	 * Reads the guide's table rows, grouped by the platform ID their label names.
	 */
	private static Map<String, List<String>> documentedRows() throws IOException {
		List<String> lines = Files.readAllLines(Path.of(System.getProperty("anvil.docs.javaGuide")));
		int header = lines.indexOf(HEADER);
		if (header < 0) throw new AssertionError("The Java guide has no table headed " + HEADER);

		Map<String, List<String>> rows = new LinkedHashMap<>();
		for (String line : lines.subList(header + 2, lines.size())) {
			if (!line.startsWith("|")) break;

			String platform = cells(line).getFirst().toLowerCase(Locale.ROOT);
			rows.computeIfAbsent(platform, ignored -> new ArrayList<>()).add(line);
		}
		return rows;
	}

	private static List<String> cells(String row) {
		List<String> cells = new ArrayList<>();
		for (String cell : row.substring(1, row.length() - 1).split("\\|")) cells.add(cell.trim());

		return cells;
	}
}
