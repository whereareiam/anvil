package me.whereareiam.anvil.environment.execution.docker.execution;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeSet;

/**
 * Docker-only mapping from an agnostic Java requirement to an immutable image reference.
 * Images are keyed by {@code <distribution>:<feature>}, such as {@code temurin:17}; requirements
 * without a distribution use {@code temurin}. Every planned LTS version needs its own mapping.
 */
@Value
@Builder
public class DockerExecutionSettings {
	/**
	 * Image references keyed by {@code <distribution>:<feature>}.
	 */
	@NotNull
	@Singular("image")
	Map<String, String> images;

	/**
	 * Returns the image mapped to the requirement's distribution and exact feature version.
	 *
	 * @param requirement planned Java requirement carrying an exact feature version
	 * @return image reference
	 * @throws ProvisioningException when the requirement has no feature version or no image is mapped
	 */
	public @NotNull String image(@NotNull JavaRequirement requirement) {
		String key = key(requirement);
		String configured = images.get(key);
		if (configured != null) return configured;

		throw new ProvisioningException("No Docker image configured for Java " + key + "; configured images: "
				+ (images.isEmpty() ? "none" : new TreeSet<>(images.keySet())));
	}

	/**
	 * Returns the key a requirement's image is mapped under, such as {@code temurin:21}.
	 *
	 * @param requirement planned Java requirement carrying an exact feature version
	 * @return {@code <distribution>:<feature>}, with {@code temurin} when the requirement names no distribution
	 * @throws ProvisioningException when the requirement has no feature version
	 */
	public @NotNull String key(@NotNull JavaRequirement requirement) {
		if (requirement.getFeatureVersion() == null)
			throw new ProvisioningException("Java requirement " + requirement + " has no feature version; platform "
					+ "planning selects an exact LTS release before execution");

		String distribution = requirement.getDistribution() == null
				? "temurin"
				: requirement.getDistribution();
		return distribution + ":" + requirement.getFeatureVersion();
	}
}
