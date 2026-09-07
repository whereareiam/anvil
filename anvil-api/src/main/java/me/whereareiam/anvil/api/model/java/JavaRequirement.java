package me.whereareiam.anvil.api.model.java;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Selects Java independently for one process. A feature version and distribution identify a Java installation; the execution provider chooses how it is supplied.
 */
@Value
@Builder(toBuilder = true)
public class JavaRequirement {
	/**
	 * Requested feature version, or null to use the platform requirement.
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
