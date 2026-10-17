package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * One release of a protocol library and the Minecraft versions it speaks.
 *
 * <p>A release speaks exactly one wire protocol, so it lists every Minecraft version sharing that
 * protocol. Selection is exact: a release never serves a version it does not list. The highest listed
 * version is the release's key, used to select version-specific code.</p>
 *
 * <pre>{@code
 * ProtocolRelease release = ProtocolRelease.builder()
 *         .libraryVersion("1.16.5-2")
 *         .minecraftVersion(MinecraftVersion.parse("1.16.4"))
 *         .minecraftVersion(MinecraftVersion.parse("1.16.5"))
 *         .verifiedVersion(MinecraftVersion.parse("1.16.5"))
 *         .protocolNumber(754)
 *         .javaVersion(8)
 *         .feature(ProtocolFeature.ONLINE_AUTHENTICATION)
 *         .build();
 * SupportLevel level = release.support(MinecraftVersion.parse("1.16.4")); // COMPATIBLE
 * }</pre>
 */
@Value
public class ProtocolRelease {
	/**
	 * Library release identifier, unique within its library.
	 */
	@NotNull String libraryVersion;
	/**
	 * Every Minecraft version speaking this release's protocol.
	 */
	@NotNull List<MinecraftVersion> minecraftVersions;
	/**
	 * Listed versions covered by Anvil's live tests.
	 */
	@NotNull Set<MinecraftVersion> verifiedVersions;
	/**
	 * Native Minecraft wire-protocol number.
	 */
	int protocolNumber;
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
	 * @param minecraftVersions every Minecraft version speaking this protocol, at least one and each once
	 * @param verifiedVersions live-tested versions, each also listed in {@code minecraftVersions}
	 * @param protocolNumber positive native wire-protocol number
	 * @param javaVersion positive minimum Java feature version of the client runtime
	 * @param features release-specific client behavior
	 * @param additional whether the release is user-supplied
	 * @param launchRefusal why no client of the release can be launched, or {@code null} when it can
	 * @throws IllegalArgumentException when the identifier is blank, no version is listed, a version is listed
	 * twice, a verified version is not listed, or the protocol number or Java version is not positive
	 */
	@Builder(toBuilder = true)
	private ProtocolRelease(
			@NotNull String libraryVersion,
			@NotNull @Singular List<MinecraftVersion> minecraftVersions,
			@NotNull @Singular Set<MinecraftVersion> verifiedVersions,
			int protocolNumber,
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
		if (protocolNumber <= 0)
			throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' declares non-positive protocol number "
					+ protocolNumber);
		if (javaVersion <= 0)
			throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' declares non-positive Java version "
					+ javaVersion);
		for (MinecraftVersion verified : verifiedVersions)
			if (!minecraftVersions.contains(verified))
				throw new IllegalArgumentException("Protocol release '" + libraryVersion + "' verifies " + verified
						+ " but does not list it");

		this.libraryVersion = libraryVersion;
		this.minecraftVersions = minecraftVersions;
		this.verifiedVersions = verifiedVersions;
		this.protocolNumber = protocolNumber;
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
