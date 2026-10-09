package me.whereareiam.anvil.api.type;

import org.jetbrains.annotations.NotNull;

/**
 * Evidence behind running one component, such as a protocol library release, a platform version or
 * a Java version, against a requested Minecraft version. Constants are ordered from strongest to
 * weakest; a combined assessment takes the weakest of its parts.
 */
public enum SupportLevel {
	/**
	 * Covered by Anvil's own live tests for exactly this version.
	 */
	VERIFIED,
	/**
	 * Declared as supported by data shipped with Anvil, but not live-tested for this version.
	 */
	COMPATIBLE,
	/**
	 * Allowed only because the user supplied the data or the version is newer than every version
	 * Anvil knows. {@link SupportPolicy#LENIENT} runs it with a warning; {@link SupportPolicy#STRICT}
	 * refuses it.
	 */
	UNTESTED,
	/**
	 * Known not to work, or outside every declared range; always refused before launch.
	 */
	UNSUPPORTED;

	/**
	 * Combines two assessments of the same run.
	 *
	 * @param other another assessment
	 * @return the weaker of both levels
	 */
	public @NotNull SupportLevel weakest(@NotNull SupportLevel other) {
		return compareTo(other) >= 0 ? this : other;
	}
}
