package me.whereareiam.anvil.execution.docker.execution;

import me.whereareiam.anvil.execution.docker.DockerEngine;
import me.whereareiam.anvil.execution.docker.DockerNetwork;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.execution.api.model.JavaCommand;
import me.whereareiam.anvil.execution.api.model.ProcessRequest;
import me.whereareiam.anvil.provisioning.cache.ArtifactCache;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import me.whereareiam.anvil.provisioning.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerEngineTest {
	private final Path directory = Path.of("build", "docker-smoke");

	@Test
	void usesTheDockerEngineApiForNetworkAndImageInspection() {
		try (DockerEngine docker = new DockerEngine()) {
			String network = "anvil-test-" + UUID.randomUUID();
            try (DockerNetwork created = docker.createNetwork(network, true)) {
                assertFalse(created.id().isBlank());
                assertTrue(docker.images().inspectMetadata("eclipse-temurin:21-jdk").id().startsWith("sha256:"));
                assertTrue(docker.images().probeJavaRuntime("eclipse-temurin:21-jdk").contains("Property settings"));
            }
		}
	}

	@Test
	void createsAttachesAndRemovesAContainerThroughTheEngineApi() throws Exception {
		try (var artifacts = new ArtifactCache(directory, true, false, 1);
			 var docker = new DockerEngine()) {
			JavaProvisioner java = new JavaProvisioner() {
				@Override public @NotNull Path resolve(@NotNull JavaRequirement requirement, int minimumVersion) { throw new UnsupportedOperationException(); }
				@Override public @NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
					return JavaInstallation.builder().executable(executable).featureVersion(21).version("21")
							.runtimeVersion("21").vendor("test").virtualMachine("test").build();
				}

				@Override public void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement, int minimumVersion) { }
			};
			ExecutionContext context = ExecutionContext.builder().cacheDirectory(directory).bindAddress("127.0.0.1")
					.javaValidator(java).artifacts(artifacts).build();
			DockerExecutionSession session = new DockerExecutionSession(context, docker,
					DockerExecutionSettings.builder().image("temurin:21", "eclipse-temurin:21-jdk").build());
			try {
				Path jar = Path.of("anvil-api/build/libs/api.jar").toAbsolutePath();
				var target = session.prepare(ProcessRequest.builder().name("smoke").workspace(directory.resolve("work"))
						.javaRequirement(JavaRequirement.builder().featureVersion(21).build()).minimumJavaVersion(21).build());
				var process = target.start(JavaCommand.builder().jar(jar).memoryMegabytes(256).build());
				assertTrue(process.await(Duration.ofSeconds(30)));
				process.terminate(true);
				target.close();
			} finally {
				session.close();
			}
		}
	}
}
