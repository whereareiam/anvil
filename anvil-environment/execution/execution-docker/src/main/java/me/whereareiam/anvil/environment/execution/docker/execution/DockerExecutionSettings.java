package me.whereareiam.anvil.environment.execution.docker.execution;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Docker-only mapping from an agnostic Java requirement to an immutable image reference. */
@Value
@Builder
public class DockerExecutionSettings {
	@NotNull
	@Singular("image")
	Map<String, String> images;

	/**
	 * Returns a configured image, or the provider's default image family.
	 *
	 * @param requirement Java requirement
	 * @param minimumVersion platform minimum
	 * @return image reference
	 */
	public @NotNull String image(@NotNull JavaRequirement requirement, int minimumVersion) {
		int version = requirement.getFeatureVersion() == null
				? minimumVersion
				: requirement.getFeatureVersion();

		String distribution = requirement.getDistribution() == null
				? "temurin"
				: requirement.getDistribution();

		String key = distribution + ":" + version;
		String configured = images.get(key);

		if (configured == null) throw new ProvisioningException("No Docker image configured for Java " + key);
		return configured;
	}
}
