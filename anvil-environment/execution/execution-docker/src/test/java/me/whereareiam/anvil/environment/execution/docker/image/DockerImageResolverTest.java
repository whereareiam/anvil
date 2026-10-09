package me.whereareiam.anvil.environment.execution.docker.image;

import me.whereareiam.anvil.api.exception.JavaVersionMismatchException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.docker.execution.DockerExecutionSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerImageResolverTest {
	@TempDir
	Path directory;

	@Test
	void namesTheImageMappingAsTheRemedyForAnImageWithAnotherJava() {
		JavaVersionMismatchException mismatch = new JavaVersionMismatchException(
				"Java at java is Java 17.0.12, but the process requires exactly Java 21");

		var failure = assertThrows(ProvisioningException.class,
				() -> resolver(mismatch).validate("java.version = 17.0.12", request(), "eclipse-temurin:17-jdk"));

		assertEquals(mismatch.getMessage() + "; the Docker execution settings map temurin:21 to image eclipse-temurin:17-jdk, "
				+ "so map temurin:21 to an image that ships Java 21", failure.getMessage());
		assertSame(mismatch, failure.getCause());
	}

	@Test
	void reportsOtherValidationFailuresWithoutTheImageMappingRemedy() {
		for (ProvisioningException other : List.of(
				new ProvisioningException("Java inspection did not report java.version"),
				new ProvisioningException("Java release mismatch: requested 21.0.4+7"))) {
			var failure = assertThrows(ProvisioningException.class,
					() -> resolver(other).validate("unreadable", request(), "eclipse-temurin:21-jdk"));

			assertSame(other, failure);
		}
	}

	private DockerImageResolver resolver(ProvisioningException validation) {
		ExecutionContext context = ExecutionContext.builder()
				.cacheDirectory(directory)
				.localRuntime((request, source) -> {
					throw new AssertionError("A Docker image needs no local Java");
				})
				.runtimeValidator((properties, request) -> {
					throw validation;
				})
				.imageLocks(lockDirectory -> {
					throw new AssertionError("Validation takes no image lock");
				})
				.build();
		DockerExecutionSettings settings = DockerExecutionSettings.builder().image("temurin:21", "eclipse-temurin:17-jdk").build();

		return new DockerImageResolver(context, null, settings);
	}

	private ProcessRequest request() {
		return ProcessRequest.builder().name("server").workspace(directory.resolve("server"))
				.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(21).build()).build())
				.build();
	}
}
