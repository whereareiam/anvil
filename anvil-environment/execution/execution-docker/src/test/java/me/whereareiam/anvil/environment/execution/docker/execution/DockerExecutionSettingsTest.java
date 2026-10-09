package me.whereareiam.anvil.environment.execution.docker.execution;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerExecutionSettingsTest {
	private final DockerExecutionSettings settings = DockerExecutionSettings.builder()
			.image("temurin:11", "eclipse-temurin:11-jdk")
			.image("temurin:17", "eclipse-temurin:17-jdk")
			.image("graalvm-community:21", "ghcr.io/graalvm/jdk-community:21")
			.build();

	@Test
	void mapsTheExactPlannedFeatureVersionPerDistribution() {
		assertEquals("eclipse-temurin:11-jdk", settings.image(JavaRequirement.builder().featureVersion(11).build()));
		assertEquals("eclipse-temurin:17-jdk", settings.image(JavaRequirement.builder().featureVersion(17).distribution("temurin").build()));
		assertEquals("ghcr.io/graalvm/jdk-community:21",
				settings.image(JavaRequirement.builder().featureVersion(21).distribution("graalvm-community").build()));
	}

	@Test
	void namesTheMissingKeyAndEveryConfiguredImage() {
		var missing = assertThrows(ProvisioningException.class, () -> settings.image(JavaRequirement.builder().featureVersion(21).build()));
		assertEquals("No Docker image configured for Java temurin:21; configured images: "
				+ "[graalvm-community:21, temurin:11, temurin:17]", missing.getMessage());

		var unplanned = assertThrows(ProvisioningException.class, () -> settings.image(JavaRequirement.builder().build()));
		assertTrue(unplanned.getMessage().contains("has no feature version"), unplanned.getMessage());
	}

	@Test
	void keysImagesByDistributionAndFeatureVersionDefaultingToTemurin() {
		assertEquals("temurin:25", settings.key(JavaRequirement.builder().featureVersion(25).build()));
		assertEquals("graalvm-community:21",
				settings.key(JavaRequirement.builder().featureVersion(21).distribution("graalvm-community").build()));
	}
}
