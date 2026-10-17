package me.whereareiam.anvil.platform.planning;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.api.type.SupportPolicy;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.planning.version.JavaCompatibility;
import me.whereareiam.anvil.platform.planning.version.PlatformVersions;
import me.whereareiam.anvil.platform.planning.version.PlatformVersionsReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Selects the exact Java feature version of each process and assesses the process against the
 * scenario's support policy before anything is downloaded or launched.
 *
 * <p>Each provider's version data is read once, when a process first needs it. The effective
 * minimum is the platform row's minimum, raised to the agent's minimum Java when the provider
 * installs an agent. Without a request, the process runs the row's preferred LTS, raised to the
 * smallest LTS at or above the effective minimum; a raised default above the row's maximum is
 * refused. A request must be an LTS release at or above the effective minimum. Above the row's
 * maximum, a platform with a bypass property runs with {@code -D<property>=true}; one without a
 * bypass is refused.</p>
 *
 * <p>The process's {@link SupportLevel} is the weakest of its platform version's level and its
 * Java level. Java is {@link SupportLevel#VERIFIED} when the data verifies it for the platform
 * version, {@link SupportLevel#UNTESTED} when it runs above the maximum through a bypass, and
 * {@link SupportLevel#COMPATIBLE} otherwise. The scenario's support policy, or the engine's, then
 * refuses the process or reports an {@code [Anvil] Info} or {@code [Anvil] Warning} line.</p>
 */
final class JavaRequirementPlanner {
	private final EngineOptions options;
	private final JavaSelection defaults;
	private final Map<String, PlatformProvider> providers;
	private final PrintStream diagnostics;
	private final PlatformVersionsReader reader = new PlatformVersionsReader();
	private final Map<String, PlatformVersions> versionsByPlatform = new ConcurrentHashMap<>();

	/**
	 * Creates a planner for one engine's providers.
	 *
	 * @param options engine options supplying the default Java selection and support policy
	 * @param providers installed platform providers keyed by platform identifier
	 * @param diagnostics stream receiving information and warning lines
	 */
	JavaRequirementPlanner(
			@NotNull EngineOptions options,
			@NotNull Map<String, PlatformProvider> providers,
			@NotNull PrintStream diagnostics
	) {
		this.options = options;
		this.defaults = options.getJavaSelection().withDefaults(JavaSelection.builder()
				.requirement(JavaRequirement.builder().build())
				.build());
		this.providers = new TreeMap<>(providers);
		this.diagnostics = diagnostics;
	}

	/**
	 * Plans Java for one validated process declaration.
	 *
	 * @param scenario scenario declaring the process
	 * @param declaration process whose platform provider is installed
	 * @return selection with an exact feature version, the JVM arguments planning adds, and the
	 * assessed support level
	 * @throws ScenarioValidationException when the version data, the version, or the Java request is refused
	 */
	@NotNull PlannedJava plan(@NotNull AnvilScenario scenario, @NotNull MinecraftProcess declaration) {
		JavaSelection selection = declaration.getJavaSelection()
				.withDefaults(scenario.getJavaSelection())
				.withDefaults(defaults);
		JavaRequirement requirement = selection.getRequirement();
		Subject subject = subject(scenario, declaration, providers.get(declaration.getPlatform()));
		SupportLevel versionLevel = subject.versionLevel();
		if (versionLevel == SupportLevel.UNSUPPORTED)
			throw new ScenarioValidationException("Process '" + subject.name() + "': " + subject.label()
					+ " is UNSUPPORTED; it is older than the first version Anvil supports for this platform ("
					+ subject.versions().firstVersion() + ").");

		JavaRange range = range(subject);
		SupportPolicy policy = scenario.getSupportPolicy() == null ? options.getSupportPolicy() : scenario.getSupportPolicy();
		Integer requested = requested(subject, requirement);
		int java = requested == null ? preferred(subject, range) : requested;
		if (requested != null) refuseUnsupportedJava(subject, range, java, policy);

		boolean bypass = range.row().exceedsMaximum(java);
		SupportLevel level = versionLevel.weakest(javaLevel(subject.versions(), subject.version(), java, bypass));
		List<String> reasons = reasons(subject, versionLevel, range, java, bypass);
		if (!policy.permits(level))
			throw new ScenarioValidationException("Process '" + subject.name() + "': " + subject.label() + " on Java "
					+ java + " is " + level + ": " + String.join("; ", reasons) + ". The " + policy
					+ " support policy refuses " + level + " processes; " + remedy(subject, versionLevel, range, bypass) + ".");

		report(subject, java, level, bypass, reasons);
		JavaSelection planned = selection.toBuilder()
				.requirement(requirement.toBuilder().featureVersion(java).build())
				.build();
		List<String> arguments = bypass ? List.of("-D" + range.row().getMaximumBypassProperty() + "=true") : List.of();

		return new PlannedJava(planned, arguments, level);
	}

	private Subject subject(AnvilScenario scenario, MinecraftProcess declaration, PlatformProvider provider) {
		try {
			return new Subject(declaration, provider, versions(provider), provider.platformVersion(declaration),
					origin(scenario, declaration), declaration.getJavaSelection().getRequirement() != null);
		} catch (PlatformException invalid) {
			throw new ScenarioValidationException("Process '" + declaration.getName() + "': " + invalid.getMessage(), invalid);
		}
	}

	private PlatformVersions versions(PlatformProvider provider) {
		return versionsByPlatform.computeIfAbsent(provider.id(), id -> reader.read(id, provider.versionData()));
	}

	private JavaRange range(Subject subject) {
		JavaCompatibility row = subject.versions().javaCompatibility(subject.version()).orElseThrow();
		if (subject.provider().platformAgent() == null) return new JavaRange(row, null);

		Integer agent = subject.versions().getAgentMinimumJava();
		if (agent != null) return new JavaRange(row, agent);

		throw new ScenarioValidationException("Process '" + subject.name() + "': platform '" + subject.provider().id()
				+ "' installs an Anvil agent, but its version data declares no [agent] minimumJava.");
	}

	private String origin(AnvilScenario scenario, MinecraftProcess declaration) {
		if (declaration.getJavaSelection().getRequirement() != null) return "process '" + declaration.getName() + "'";
		if (scenario.getJavaSelection().getRequirement() != null) return "scenario '" + scenario.getName() + "'";

		return "the engine Java selection";
	}

	private @Nullable Integer requested(Subject subject, JavaRequirement requirement) {
		Integer feature = requirement.getFeatureVersion();
		String release = requirement.getRelease();
		if (release == null) return feature;

		Integer releaseFeature = releaseFeature(release);
		if (feature == null && releaseFeature == null)
			throw new ScenarioValidationException("Process '" + subject.name() + "' requests Java release '" + release
					+ "' (" + subject.requestedBy() + "), which is not a Java version string; set featureVersion as well."
					+ subject.overrideHint());
		if (feature == null) return releaseFeature;
		if (releaseFeature != null && !releaseFeature.equals(feature))
			throw new ScenarioValidationException("Process '" + subject.name() + "' requests Java " + feature
					+ " with release " + release + " (" + subject.requestedBy() + "), but that release belongs to Java "
					+ releaseFeature + "." + subject.overrideHint());

		return feature;
	}

	private @Nullable Integer releaseFeature(String release) {
		try {
			return Runtime.Version.parse(release).feature();
		} catch (IllegalArgumentException invalid) {
			return null;
		}
	}

	private int preferred(Subject subject, JavaRange range) {
		if (range.defaultFits()) return range.preferred();

		JavaCompatibility row = range.row();
		String bypass = row.getMaximumBypassProperty() == null ? "" : " Request Java " + range.preferred()
				+ " explicitly to run it above the maximum with -D" + row.getMaximumBypassProperty() + "=true.";
		throw new ScenarioValidationException("Process '" + subject.name() + "': " + subject.label() + " runs at most Java "
				+ row.getMaximum() + ", but the Anvil agent installed into " + subject.provider().id() + " requires Java "
				+ range.agentMinimum() + " or newer, so no LTS release runs both by default." + bypass);
	}

	private void refuseUnsupportedJava(Subject subject, JavaRange range, int java, SupportPolicy policy) {
		JavaCompatibility row = range.row();
		String request = "Process '" + subject.name() + "' requests Java " + java + " (" + subject.requestedBy() + ")";
		String accepted = " " + subject.label() + " " + describe(range) + "." + subject.overrideHint();
		if (!JavaCompatibility.isLongTermSupport(java))
			throw new ScenarioValidationException(request + ", which is not an LTS release. Anvil runs processes on LTS "
					+ "releases only: 11, 17, 21, 25, then every fourth release." + accepted);
		if (range.agentMinimum() != null && java < range.agentMinimum())
			throw new ScenarioValidationException(request + ", but the Anvil agent installed into "
					+ subject.provider().id() + " requires Java " + range.agentMinimum() + " or newer." + accepted);
		if (java < row.getMinimum())
			throw new ScenarioValidationException(request + ", but " + subject.label() + " requires Java "
					+ row.getMinimum() + " or newer." + accepted);
		if (!row.exceedsMaximum(java) || row.getMaximumBypassProperty() != null) return;

		List<String> remedies = new ArrayList<>();
		if (range.defaultFits()) remedies.add("Remove the Java requirement to use Java " + range.preferred() + " (default)");
		List<String> platforms = alternatives(subject, java, policy);
		if (!platforms.isEmpty()) remedies.add((remedies.isEmpty() ? "Use " : "use ") + String.join(" or ", platforms));
		String remedy = remedies.isEmpty() ? "" : " " + String.join(", or ", remedies) + ".";
		throw new ScenarioValidationException(request + ", but " + subject.label() + " refuses Java above "
				+ row.getMaximum() + " and has no bypass." + remedy + subject.overrideHint());
	}

	private List<String> alternatives(Subject subject, int java, SupportPolicy policy) {
		if (!(subject.declaration() instanceof MinecraftServer server)) return List.of();

		List<String> platforms = new ArrayList<>();
		for (PlatformProvider other : providers.values()) {
			if (other == subject.provider() || !other.configurationType().isInstance(server)) continue;

			String alternative = alternative(other, server, java, policy);
			if (alternative != null) platforms.add(alternative);
		}

		return platforms;
	}

	private @Nullable String alternative(PlatformProvider other, MinecraftServer server, int java, SupportPolicy policy) {
		MinecraftVersion version;
		PlatformVersions data;
		try {
			version = other.platformVersion(server);
			data = versions(other);
		} catch (PlatformException invalid) {
			return null;
		}

		JavaCompatibility row = version == null ? null : data.javaCompatibility(version).orElse(null);
		if (row == null) return null;
		Integer agent = other.platformAgent() == null ? null : data.getAgentMinimumJava();
		if (other.platformAgent() != null && agent == null) return null;
		if (java < new JavaRange(row, agent).minimum()) return null;
		boolean bypass = row.exceedsMaximum(java);
		if (bypass && row.getMaximumBypassProperty() == null) return null;
		if (!policy.permits(data.support(version).weakest(javaLevel(data, version, java, bypass)))) return null;

		String alternative = "'" + other.id() + "', which runs Minecraft " + version + " on Java " + java;
		return bypass ? alternative + " with -D" + row.getMaximumBypassProperty() + "=true" : alternative;
	}

	private static SupportLevel javaLevel(PlatformVersions versions, @Nullable MinecraftVersion version, int java, boolean bypass) {
		if (versions.verifiedJava(version).contains(java)) return SupportLevel.VERIFIED;
		if (bypass) return SupportLevel.UNTESTED;

		return SupportLevel.COMPATIBLE;
	}

	private List<String> reasons(Subject subject, SupportLevel versionLevel, JavaRange range, int java, boolean bypass) {
		List<String> reasons = new ArrayList<>();
		if (subject.version() == null)
			reasons.add("the " + subject.provider().id() + " distribution carries no version Anvil can assess");
		if (versionLevel == SupportLevel.COMPATIBLE)
			reasons.add(subject.label() + " is a known version but is not verified");
		if (versionLevel == SupportLevel.UNTESTED)
			reasons.add(subject.label() + " is not a version Anvil knows (newest known: "
					+ subject.versions().newestKnownVersion().map(MinecraftVersion::toString).orElse("none") + ")");

		Set<Integer> verified = subject.versions().verifiedJava(subject.version());
		String verifiedJava = verified.isEmpty()
				? "no Java version is verified for " + subject.label()
				: "verified Java for " + subject.label() + ": " + javaList(verified);
		if (bypass) {
			reasons.add("Java " + java + " is above the maximum Java " + range.row().getMaximum() + " for this version and runs with -D"
					+ range.row().getMaximumBypassProperty() + "=true; " + (verified.contains(java)
					? "Anvil's live matrix verifies this combination although the platform does not support it"
					: verifiedJava));
			return reasons;
		}

		if (!verified.contains(java))
			reasons.add("Java " + java + " is inside the accepted range (" + bounds(range) + ") but " + verifiedJava);
		return reasons;
	}

	private String remedy(Subject subject, SupportLevel versionLevel, JavaRange range, boolean bypass) {
		List<String> advice = new ArrayList<>();
		if (versionLevel == SupportLevel.UNTESTED) advice.add("use a version Anvil knows");
		if (bypass) {
			Set<Integer> verified = subject.versions().verifiedJava(subject.version());
			List<String> java = new ArrayList<>();
			if (range.defaultFits()) java.add("Java " + range.preferred() + " (default)");
			if (!verified.isEmpty()) java.add("a verified Java version (" + javaList(verified) + ")");
			if (!java.isEmpty()) advice.add("use " + String.join(" or ", java));
		}

		if (advice.isEmpty()) return "run under the LENIENT support policy";
		return String.join(" and ", advice) + ", or run under the LENIENT support policy";
	}

	private void report(Subject subject, int java, SupportLevel level, boolean bypass, List<String> reasons) {
		if (level == SupportLevel.VERIFIED && !bypass) return;

		String prefix = level == SupportLevel.UNTESTED ? "[Anvil] Warning: " : "[Anvil] Info: ";
		String suffix = level == SupportLevel.UNTESTED ? " The STRICT support policy refuses this process." : "";
		diagnostics.println(prefix + "process '" + subject.name() + "' runs " + subject.label() + " on Java " + java
				+ " (" + level + "): " + String.join("; ", reasons) + "." + suffix);
	}

	private static String bounds(JavaRange range) {
		if (range.row().getMaximum() == null) return "Java " + range.minimum() + " or newer";

		return "Java " + range.minimum() + " to " + range.row().getMaximum();
	}

	private static String describe(JavaRange range) {
		JavaCompatibility row = range.row();
		String text = "runs " + bounds(new JavaRange(row, null));
		if (range.raisedByAgent()) text += ", and its Anvil agent requires Java " + range.agentMinimum() + " or newer";
		if (range.defaultFits()) text += " (default " + range.preferred() + ")";
		if (row.getMaximum() == null) return text;
		if (row.getMaximumBypassProperty() == null) return text + " and refuses newer Java";

		return text + "; newer Java runs only with -D" + row.getMaximumBypassProperty() + "=true";
	}

	private static String javaList(Set<Integer> versions) {
		return versions.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
	}

	/**
	 * Java planned for one process.
	 *
	 * @param selection effective selection whose requirement carries the exact feature version
	 * @param jvmArguments JVM arguments planning adds before platform defaults, such as a bypass property
	 * @param support assessed support level of the process
	 */
	record PlannedJava(
			@NotNull JavaSelection selection,
			@NotNull List<String> jvmArguments,
			@NotNull SupportLevel support
	) {
	}

	/**
	 * The Java one platform version accepts with the agent its provider installs.
	 *
	 * @param row platform row for the version
	 * @param agentMinimum oldest Java the installed agent runs on, or null without an agent
	 */
	private record JavaRange(@NotNull JavaCompatibility row, @Nullable Integer agentMinimum) {
		int minimum() {
			return agentMinimum == null ? row.getMinimum() : Math.max(row.getMinimum(), agentMinimum);
		}

		int preferred() {
			if (row.getPreferred() >= minimum()) return row.getPreferred();

			return JavaCompatibility.nextLongTermSupport(minimum());
		}

		boolean defaultFits() {
			return !row.exceedsMaximum(preferred());
		}

		boolean raisedByAgent() {
			return agentMinimum != null && agentMinimum > row.getMinimum();
		}
	}

	private record Subject(
			@NotNull MinecraftProcess declaration,
			@NotNull PlatformProvider provider,
			@NotNull PlatformVersions versions,
			@Nullable MinecraftVersion version,
			@NotNull String origin,
			boolean ownRequirement
	) {
		String name() {
			return declaration.getName();
		}

		String label() {
			return version == null ? provider.id() : provider.id() + " " + version;
		}

		SupportLevel versionLevel() {
			return version == null ? SupportLevel.VERIFIED : versions.support(version);
		}

		String requestedBy() {
			return "requested by " + origin;
		}

		String overrideHint() {
			if (ownRequirement) return "";

			return " The request comes from " + origin + " and applies to every process without its own Java "
					+ "requirement; declare a JavaRequirement on process '" + name() + "' to override it.";
		}
	}
}
