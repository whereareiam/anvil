package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One release of a protocol library: the Minecraft edition it implements, the versions of that edition it
 * speaks and the native wire-protocol number of each.
 *
 * <p>A release serves exactly the versions it lists, and only for servers of its {@link MinecraftEdition}:
 * selection compares the edition first, so a release never serves a server of another edition that numbers a
 * version alike. Several listed versions may share one protocol number, or each may have its own. The highest
 * listed version is the release's key, used to select version-specific code.</p>
 *
 * <pre>{@code
 * MinecraftVersion older = MinecraftVersion.parse("1.16.4");
 * MinecraftVersion newer = MinecraftVersion.parse("1.16.5");
 * ProtocolRelease release = ProtocolRelease.builder()
 *         .libraryVersion("1.16.5-2")
 *         .edition(MinecraftEdition.of("example"))
 *         .minecraftVersion(older)
 *         .minecraftVersion(newer)
 *         .verifiedVersion(newer)
 *         .protocolNumber(older, 754)
 *         .protocolNumber(newer, 754)
 *         .javaVersion(8)
 *         .feature(ProtocolFeature.ONLINE_AUTHENTICATION)
 *         .build();
 * SupportLevel level = release.support(older); // COMPATIBLE
 * int protocol = release.protocolNumber(older); // 754
 * }</pre>
 */
@Value
public class ProtocolRelease {
	/**
	 * Library release identifier, unique within its library.
	 */
	@NotNull String libraryVersion;
	/**
	 * Minecraft edition whose servers this release reaches.
	 */
	@NotNull MinecraftEdition edition;
	/**
	 * Every Minecraft version of the edition this release speaks.
	 */
	@NotNull List<MinecraftVersion> minecraftVersions;
	/**
	 * Listed versions covered by Anvil's live tests.
	 */
	@NotNull Set<MinecraftVersion> verifiedVersions;
	/**
	 * Native wire-protocol number of each listed Minecraft version.
	 */
	@NotNull Map<MinecraftVersion, Integer> protocolNumbers;
	/**
	 * Minimum Java feature version required by the client runtime.
	 */
	int javaVersion;
	/**
	 * Release-specific client behavior; features never select player capability providers.
	 */
	@NotNull Set<ProtocolFeature> features;
	/**
	 * Whether the release comes from user-supplied data rather than data shipped with Anvil.
	 */
	boolean additional;
	/**
	 * Why no client of this release can be launched, such as an artifact without a pinned checksum, or
	 * {@code null} when it can. Creating a player that selects such a release is refused.
	 *
	 * <p>The library derives the refusal from its release data, and its wording, such as the remedy it names,
	 * may depend on where the data came from. It is therefore not part of equality: a user-supplied copy of a
	 * built-in release equals that release.</p>
	 */
	@EqualsAndHashCode.Exclude
	@Nullable String launchRefusal;

	/**
	 * Creates a validated release.
	 *
	 * @param libraryVersion non-blank library release identifier
	 * @param edition Minecraft edition whose servers the release reaches
	 * @param minecraftVersions every Minecraft version the release speaks, at least one and each once
	 * @param verifiedVersions live-tested versions, each also listed in {@code minecraftVersions}
	 * @param protocolNumbers positive native wire-protocol number of exactly the listed versions
	 * @param javaVersion positive minimum Java feature version of the client runtime
	 * @param features release-specific client behavior
	 * @param additional whether the release is user-supplied
	 * @param launchRefusal why no client of the release can be launched, or {@code null} when it can
	 * @throws IllegalArgumentException when the identifier is blank, no version is listed, a version is listed
	 * twice, a verified version is not listed, a listed version has no protocol number or an unlisted one has
	 * one, or a protocol number or the Java version is not positive
	 */
	@Builder(toBuilder = true)
	private ProtocolRelease(
			@NotNull String libraryVersion,
			@NotNull MinecraftEdition edition,
			@NotNull @Singular List<MinecraftVersion> minecraftVersions,
			@NotNull @Singular Set<MinecraftVersion> verifiedVersions,
			@NotNull @Singular Map<MinecraftVersion, Integer> protocolNumbers,
			int javaVersion,
			@NotNull @Singular Set<ProtocolFeature> features,
			boolean additional,
			@Nullable String launchRefusal
	) {
		if (libraryVersion.isBlank()) throw new IllegalArgumentException("Protocol release version must not be blank");
		if (minecraftVersions.isEmpty())
			throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' lists no Minecraft versions");
		if (Set.copyOf(minecraftVersions).size() != minecraftVersions.size())
			throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' lists a Minecraft version twice: "
					+ minecraftVersions);
		if (!protocolNumbers.keySet().equals(Set.copyOf(minecraftVersions)))
			throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' must declare one protocol number for "
					+ "each listed Minecraft version " + minecraftVersions + ", not for " + protocolNumbers.keySet());
		for (var protocol : protocolNumbers.entrySet())
			if (protocol.getValue() <= 0)
				throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' declares non-positive protocol number "
						+ protocol.getValue() + " for Minecraft " + protocol.getKey());
		if (javaVersion <= 0)
			throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' declares non-positive Java version "
					+ javaVersion);
		for (MinecraftVersion verified : verifiedVersions)
			if (!minecraftVersions.contains(verified))
				throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' verifies " + verified
						+ " but does not list it");

		this.libraryVersion = libraryVersion;
		this.edition = edition;
		this.minecraftVersions = minecraftVersions;
		this.verifiedVersions = verifiedVersions;
		this.protocolNumbers = protocolNumbers;
		this.javaVersion = javaVersion;
		this.features = features;
		this.additional = additional;
		this.launchRefusal = launchRefusal;
	}

	/**
	 * Tests whether a client of this release can be launched.
	 *
	 * @return whether {@link #getLaunchRefusal()} is {@code null}
	 */
	public boolean isLaunchable() {
		return launchRefusal == null;
	}

	/**
	 * Returns the release key: the highest Minecraft version speaking this protocol.
	 *
	 * @return release key version
	 */
	public @NotNull MinecraftVersion version() {
		return Collections.max(minecraftVersions);
	}

	/**
	 * Returns the native wire-protocol number a client of this release speaks for one listed version.
	 *
	 * @param version Minecraft version the release lists
	 * @return positive protocol number
	 * @throws IllegalArgumentException when the release does not list the version
	 */
	public int protocolNumber(@NotNull MinecraftVersion version) {
		Integer protocol = protocolNumbers.get(version);
		if (protocol == null)
			throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' does not list Minecraft " + version);

		return protocol;
	}

	/**
	 * Tests whether this release serves a server of an edition and version.
	 *
	 * @param edition the server's Minecraft edition
	 * @param version the server's Minecraft version
	 * @return whether the release implements the edition and lists the version
	 */
	public boolean serves(@NotNull MinecraftEdition edition, @NotNull MinecraftVersion version) {
		return this.edition.equals(edition) && minecraftVersions.contains(version);
	}

	/**
	 * Assesses this release for one Minecraft version.
	 *
	 * @param version requested Minecraft version
	 * @return {@link SupportLevel#UNSUPPORTED} when the version is not listed, {@link SupportLevel#UNTESTED}
	 * for user-supplied releases, {@link SupportLevel#VERIFIED} for live-tested versions, and
	 * {@link SupportLevel#COMPATIBLE} otherwise
	 */
	public @NotNull SupportLevel support(@NotNull MinecraftVersion version) {
		if (!minecraftVersions.contains(version)) return SupportLevel.UNSUPPORTED;
		if (additional) return SupportLevel.UNTESTED;
		if (verifiedVersions.contains(version)) return SupportLevel.VERIFIED;

		return SupportLevel.COMPATIBLE;
	}
}
