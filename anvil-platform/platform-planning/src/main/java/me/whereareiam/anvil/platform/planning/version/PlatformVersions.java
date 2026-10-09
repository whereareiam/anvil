package me.whereareiam.anvil.platform.planning.version;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Version data of one platform provider: Java compatibility rows, the oldest Java the provider's
 * agent runs on, the Java versions verified for each platform version, and every platform version
 * the data declares as supported. {@link PlatformVersionsReader} reads it from the provider's
 * {@code <platform>-versions.toml} resource.
 *
 * <p>Platform versions are the Minecraft version for servers and the proxy's own release for
 * proxies keyed by release. Construction validates the data: rows are ordered by {@code since} and
 * only the first may omit it, each preferred version is an LTS release inside its row, a bypass
 * needs a maximum, every verified or known version has a row, and verified Java runs the agent.</p>
 */
@Value
public class PlatformVersions {
	/**
	 * Java compatibility rows ordered by their start version.
	 */
	@NotNull List<JavaCompatibility> javaCompatibilities;

	/**
	 * Oldest Java feature version the agent installed by the provider runs on, or null when the
	 * data declares no agent.
	 */
	@Nullable Integer agentMinimumJava;

	/**
	 * Java feature versions verified for each platform version.
	 */
	@NotNull Map<MinecraftVersion, Set<Integer>> verifiedJavaVersions;

	/**
	 * Platform versions the data declares as supported; verified versions are implicitly known.
	 */
	@NotNull Set<MinecraftVersion> knownVersions;

	@Builder
	private PlatformVersions(
			@NotNull @Singular List<JavaCompatibility> javaCompatibilities,
			@Nullable Integer agentMinimumJava,
			@NotNull @Singular Map<MinecraftVersion, Set<Integer>> verifiedJavaVersions,
			@NotNull @Singular Set<MinecraftVersion> knownVersions
	) {
		if (javaCompatibilities.isEmpty())
			throw new IllegalArgumentException("Platform version data needs at least one Java row");
		if (agentMinimumJava != null && agentMinimumJava < 1)
			throw new IllegalArgumentException("The agent's minimum Java must be a Java feature version, not " + agentMinimumJava);

		List<JavaCompatibility> rows = new ArrayList<>(javaCompatibilities);
		rows.sort(Comparator.comparing(JavaCompatibility::getSince, Comparator.nullsFirst(Comparator.naturalOrder())));
		for (int index = 0; index < rows.size(); index++) {
			JavaCompatibility row = rows.get(index);
			if (index > 0 && row.getSince() == null)
				throw new IllegalArgumentException("Only the first Java row may omit 'since'");
			if (index > 0 && row.getSince().equals(rows.get(index - 1).getSince()))
				throw new IllegalArgumentException("Duplicate Java row for " + row.getSince());
			validate(row);
		}

		this.javaCompatibilities = List.copyOf(rows);
		this.agentMinimumJava = agentMinimumJava;
		Map<MinecraftVersion, Set<Integer>> verified = new LinkedHashMap<>();
		for (var entry : verifiedJavaVersions.entrySet()) {
			JavaCompatibility row = row(entry.getKey());
			for (int java : entry.getValue())
				validateVerified(entry.getKey(), java, row, agentMinimumJava);
			verified.put(entry.getKey(), Set.copyOf(entry.getValue()));
		}

		this.verifiedJavaVersions = Map.copyOf(verified);
		knownVersions.forEach(this::row);
		this.knownVersions = Set.copyOf(knownVersions);
	}

	/**
	 * Returns the Java row applying to a platform version: the row with the greatest start version
	 * not newer than it. A process without a version uses the newest row.
	 *
	 * @param version platform version, or null when the process carries none
	 * @return the applicable row, or empty when the version is older than the first row
	 */
	public @NotNull Optional<JavaCompatibility> javaCompatibility(@Nullable MinecraftVersion version) {
		if (version == null) return Optional.of(javaCompatibilities.getLast());

		JavaCompatibility applicable = null;
		for (JavaCompatibility row : javaCompatibilities) {
			if (!row.covers(version)) break;
			applicable = row;
		}

		return Optional.ofNullable(applicable);
	}

	/**
	 * Assesses a platform version against this data.
	 *
	 * <ul>
	 *     <li>{@link SupportLevel#VERIFIED}: verified on at least one Java version;</li>
	 *     <li>{@link SupportLevel#COMPATIBLE}: listed as a known version;</li>
	 *     <li>{@link SupportLevel#UNTESTED}: newer than every known version, or an unknown version
	 *     inside the supported range;</li>
	 *     <li>{@link SupportLevel#UNSUPPORTED}: older than the first Java row.</li>
	 * </ul>
	 *
	 * @param version platform version
	 * @return support level of the version itself, independent of Java
	 */
	public @NotNull SupportLevel support(@NotNull MinecraftVersion version) {
		if (verifiedJavaVersions.containsKey(version)) return SupportLevel.VERIFIED;
		if (javaCompatibility(version).isEmpty()) return SupportLevel.UNSUPPORTED;
		if (knownVersions.contains(version)) return SupportLevel.COMPATIBLE;

		return SupportLevel.UNTESTED;
	}

	/**
	 * Returns the Java feature versions verified for a platform version.
	 *
	 * @param version platform version, or null when the process carries none
	 * @return verified Java feature versions, empty when the version is null or not verified
	 */
	public @NotNull Set<Integer> verifiedJava(@Nullable MinecraftVersion version) {
		if (version == null) return Set.of();

		return verifiedJavaVersions.getOrDefault(version, Set.of());
	}

	/**
	 * Returns the oldest platform version any Java row covers.
	 *
	 * @return start version of the first row, or null when the first row applies from the oldest version
	 */
	public @Nullable MinecraftVersion firstVersion() {
		return javaCompatibilities.getFirst().getSince();
	}

	/**
	 * Returns the newest platform version the data knows, including verified versions.
	 *
	 * @return newest known version, or empty when the data names none
	 */
	public @NotNull Optional<MinecraftVersion> newestKnownVersion() {
		TreeSet<MinecraftVersion> versions = new TreeSet<>(knownVersions);
		versions.addAll(verifiedJavaVersions.keySet());

		return versions.isEmpty() ? Optional.empty() : Optional.of(versions.last());
	}

	private JavaCompatibility row(MinecraftVersion version) {
		return javaCompatibility(version).orElseThrow(() -> new IllegalArgumentException(
				"Version " + version + " is older than the first Java row (" + firstVersion() + ")"));
	}

	private static void validate(JavaCompatibility row) {
		String label = row.getSince() == null ? "The first Java row" : "Java row since " + row.getSince();
		if (row.getMinimum() > row.getPreferred())
			throw new IllegalArgumentException(label + " prefers Java " + row.getPreferred()
					+ " below its minimum " + row.getMinimum());
		if (row.getMaximum() != null && row.getPreferred() > row.getMaximum())
			throw new IllegalArgumentException(label + " prefers Java " + row.getPreferred()
					+ " above its maximum " + row.getMaximum());
		if (!JavaCompatibility.isLongTermSupport(row.getPreferred()))
			throw new IllegalArgumentException(label + " prefers Java " + row.getPreferred() + ", which is not an LTS release");
		if (row.getMaximumBypassProperty() == null) return;
		if (row.getMaximum() == null)
			throw new IllegalArgumentException(label + " names a maximum bypass property without a maximum");
		if (row.getMaximumBypassProperty().isBlank())
			throw new IllegalArgumentException(label + " names a blank maximum bypass property");
	}

	private static void validateVerified(MinecraftVersion version, int java, JavaCompatibility row, @Nullable Integer agent) {
		String label = "Verified Java " + java + " for " + version;
		if (!JavaCompatibility.isLongTermSupport(java))
			throw new IllegalArgumentException(label + " is not an LTS release");
		if (java < row.getMinimum())
			throw new IllegalArgumentException(label + " is below the row minimum " + row.getMinimum());
		if (agent != null && java < agent)
			throw new IllegalArgumentException(label + " is below the agent's minimum Java " + agent);
		if (row.exceedsMaximum(java) && row.getMaximumBypassProperty() == null)
			throw new IllegalArgumentException(label + " is above the row maximum " + row.getMaximum()
					+ " and the row has no bypass");
	}
}
