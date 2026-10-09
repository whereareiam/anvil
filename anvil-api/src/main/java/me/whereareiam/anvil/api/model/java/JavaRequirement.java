package me.whereareiam.anvil.api.model.java;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * Selects Java independently for one process. A feature version and distribution identify a Java
 * installation; the execution provider chooses how it is supplied. Anvil runs processes on LTS
 * releases only (11, 17, 21, 25, then every fourth release); platform planning checks a requested
 * version against the platform's Java range before anything is downloaded or launched.
 */
@Value
@Builder(toBuilder = true)
public class JavaRequirement {
	/**
	 * Requested LTS feature version, or null to use the platform's preferred LTS release for the
	 * process version. Planning always replaces null with an exact feature version.
	 */
	@Nullable Integer featureVersion;
	/**
	 * Distribution identifier, such as temurin or graalvm-community.
	 */
	@Nullable String distribution;
	/**
	 * Exact JDK release, including the build when supplied.
	 */
	@Nullable String release;
}
