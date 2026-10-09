package me.whereareiam.anvil.platform.planning.version;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java feature versions one platform starts on, from {@link #getSince() since} until the next row
 * of its {@link PlatformVersions} starts.
 *
 * <p>The {@link #getPreferred() preferred} version is an LTS release inside
 * [{@link #getMinimum() minimum}, {@link #getMaximum() maximum}]; planning selects it when no
 * declaration requests a Java version and the platform's agent runs on it. A maximum is the
 * platform's own startup refusal: Java above it runs only when the platform names a
 * {@link #getMaximumBypassProperty() bypass property}, and planning adds that property only for an
 * explicitly requested version.</p>
 */
@Value
@Builder
public class JavaCompatibility {
	/**
	 * Platform version this row starts at: the Minecraft version for servers, or the proxy's own
	 * release for proxies whose data is keyed by release. Null only for a first row that applies
	 * from the oldest version, such as the single row of a platform whose builds carry no version.
	 */
	@Nullable MinecraftVersion since;

	/**
	 * Lowest Java feature version the platform starts on.
	 */
	int minimum;

	/**
	 * Highest Java feature version the platform starts on without a bypass, or null when the
	 * platform has no maximum in this range.
	 */
	@Nullable Integer maximum;

	/**
	 * LTS feature version used when no declaration requests one.
	 */
	int preferred;

	/**
	 * Boolean system property that makes the platform start above {@link #getMaximum() maximum},
	 * or null when the platform has no bypass.
	 */
	@Nullable String maximumBypassProperty;

	/**
	 * Tests whether a Java feature version is a release Anvil runs processes on: 11, 17, and every
	 * fourth release after 17 (21, 25, 29, ...). Java 8 is older than the Anvil agent and the
	 * provisioning floor, so it is not one of them.
	 *
	 * @param featureVersion Java feature version
	 * @return whether the version is an LTS release Anvil accepts
	 */
	public static boolean isLongTermSupport(int featureVersion) {
		if (featureVersion == 11) return true;

		return featureVersion >= 17 && (featureVersion - 17) % 4 == 0;
	}

	/**
	 * Returns the smallest LTS release that is not older than a Java feature version.
	 *
	 * @param featureVersion lowest acceptable Java feature version
	 * @return smallest LTS release at or above the version
	 */
	public static int nextLongTermSupport(int featureVersion) {
		if (featureVersion <= 11) return 11;
		if (featureVersion <= 17) return 17;

		return 17 + (featureVersion - 17 + 3) / 4 * 4;
	}

	/**
	 * Tests whether this row applies to a platform version on its own start: the version is not
	 * older than {@link #getSince() since}.
	 *
	 * @param version platform version
	 * @return whether the version starts no earlier than this row
	 */
	public boolean covers(@NotNull MinecraftVersion version) {
		return since == null || since.compareTo(version) <= 0;
	}

	/**
	 * Tests whether a Java feature version is above this row's maximum.
	 *
	 * @param featureVersion Java feature version
	 * @return whether the platform refuses the version unless a bypass is set
	 */
	public boolean exceedsMaximum(int featureVersion) {
		return maximum != null && featureVersion > maximum;
	}
}
