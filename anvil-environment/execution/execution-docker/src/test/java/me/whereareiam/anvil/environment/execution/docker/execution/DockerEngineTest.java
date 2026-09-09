package me.whereareiam.anvil.environment.execution.docker.execution;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.docker.DockerEngine;
import me.whereareiam.anvil.environment.execution.docker.DockerNetwork;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class DockerEngineTest {
	private final Path directory = Path.of("build", "docker-smoke", UUID.randomUUID().toString()).toAbsolutePath();

	@AfterEach
	void removePreparedFiles() throws IOException {
		if (!Files.exists(directory)) return;
		try (var files = Files.walk(directory)) {
			for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
		}
	}

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
	void coordinatesAndValidatesTheImageThenRunsThePreparedCommand() throws Exception {
		ReentrantLock metadata = new ReentrantLock();
		AtomicBoolean validated = new AtomicBoolean();
		ExecutionContext context = ExecutionContext.builder().cacheDirectory(directory).bindAddress("127.0.0.1")
				.localRuntime((request, source) -> { throw new AssertionError("Docker must not acquire host Java"); })
				.imageLocks(path -> {
					assertTrue(path.startsWith(directory.resolve("docker-images")));
					metadata.lock();
					return metadata::unlock;
				})
				.runtimeValidator((properties, request) -> {
					assertTrue(metadata.isHeldByCurrentThread());
					assertTrue(properties.contains("java.specification.version = 21"));
					assertEquals(21, request.getMinimumJavaVersion());
					validated.set(true);
				}).build();
		try (var docker = new DockerEngine()) {
			try (DockerExecutionSession session = new DockerExecutionSession(context, docker,
					DockerExecutionSettings.builder().image("temurin:21", "eclipse-temurin:21-jdk").build())) {
				Path workspace = directory.resolve("work");
				Files.createDirectories(workspace);
				Path jar = Files.copy(FixtureArtifacts.process(), workspace.resolve("process.jar"));
				var request = ProcessRequest.builder().name("smoke").workspace(workspace)
						.javaRequirement(JavaRequirement.builder().featureVersion(21).build()).minimumJavaVersion(21).build();
				try (var target = session.prepare(request)) {
					assertTrue(validated.get());
					assertFalse(metadata.isLocked());
					var process = target.start(JavaCommand.builder().jar(jar).memoryMegabytes(256).build());
					try {
						var output = new BufferedReader(new InputStreamReader(process.output(), StandardCharsets.UTF_8));
						assertEquals("READY", output.readLine());
						process.input().write("stop\n".getBytes(StandardCharsets.UTF_8));
						process.input().flush();
						assertTrue(process.await(Duration.ofSeconds(30)));
					} finally {
						process.terminate(true);
					}
				}
			}
		}
	}
}
