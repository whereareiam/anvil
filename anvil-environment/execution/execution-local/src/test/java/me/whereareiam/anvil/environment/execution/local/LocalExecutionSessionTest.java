package me.whereareiam.anvil.environment.execution.local;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.runtime.LocalRuntimePreparation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalExecutionSessionTest {
	@TempDir
	Path directory;

	@Test
	void resolvesJavaRequirementsIndependentlyForEachProcess() {
		List<ProcessRequest> requests = new ArrayList<>();
		LocalRuntimePreparation runtime = (request, source) -> {
			requests.add(request);
			return Path.of("java-" + request.getJavaSelection().getRequirement().getDistribution());
		};
		JavaRequirement temurin = JavaRequirement.builder().distribution("temurin").featureVersion(21).build();
		JavaRequirement graal = JavaRequirement.builder().distribution("graalvm-community").featureVersion(21).build();
		ProcessRequest first = request("temurin", temurin, null);
		ProcessRequest second = request("graal", graal, null);
		try (ExecutionSession session = new LocalExecutionProvider().open(context(runtime))) {
			var a = session.prepare(first);
			var b = session.prepare(second);
			assertNotEquals(a.address(), b.address());
			assertNotEquals(a.agentAddress(), b.agentAddress());
		}
		assertEquals(List.of(first, second), requests);
	}

	@Test
	void prefersExplicitSourcesThenProviderDefaults() {
		List<JavaSource> sources = new ArrayList<>();
		LocalRuntimePreparation runtime = (request, source) -> {
			sources.add(source);
			return Path.of("selected-java");
		};
		Path fallback = directory.resolve("provider-jdk");
		JavaSource explicit = JavaSource.executable(directory.resolve("explicit/bin/java"));
		var provider = new LocalExecutionProvider(LocalExecutionSettings.builder().javaHome("default:21", fallback).build(), runtime);
		try (ExecutionSession session = provider.open(context((request, source) -> { throw new AssertionError("Override must win"); }))) {
			JavaRequirement requirement = JavaRequirement.builder().featureVersion(21).build();
			session.prepare(request("explicit", requirement, explicit));
			session.prepare(request("fallback", requirement, null));
		}
		assertEquals(List.of(explicit, new LocalJavaHome(fallback)), sources);
	}

	@Test
	void rejectsIsolationItCannotEnforceBeforeAcquiringTheRuntime() {
		ExecutionContext context = context((request, source) -> { throw new AssertionError("Must reject first"); }).toBuilder()
				.networkPolicy(NetworkPolicy.builder().networkServerAccess(NetworkServerAccess.PROXY_ONLY)
						.backendNetworkExposure(NetworkExposure.PRIVATE).build()).build();
		try (ExecutionSession session = new LocalExecutionProvider().open(context)) {
			assertThrows(ProvisioningException.class, () -> session.prepare(request("server", JavaRequirement.builder().featureVersion(21).build(), null)));
		}
	}

	@Test
	void usesTheGroupedNetworkBindingWithoutChangingAgentExposure() {
		var context = context((request, source) -> Path.of("java")).toBuilder()
				.networkPolicy(NetworkPolicy.builder().bindAddress("127.0.0.2").build())
				.build();
		try (ExecutionSession session = new LocalExecutionProvider().open(context)) {
			var target = session.prepare(request("server", JavaRequirement.builder().featureVersion(21).build(), null));
			assertEquals("127.0.0.2", target.address().getHostString());
			assertTrue(target.agentAddress().getAddress().isLoopbackAddress());
		}
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void startsProcessesWithALowerPriorityOnlyWhenAsked() throws Exception {
		assertEquals(niceness(ProcessPriority.NORMAL) + 10, niceness(ProcessPriority.LOW));
	}

	/**
	 * Starts a stand-in for the Java executable that reports the niceness it runs with.
	 */
	private int niceness(ProcessPriority priority) throws Exception {
		Path executable = Files.createDirectories(directory.resolve(priority.name())).resolve("java");
		Path report = executable.resolveSibling("niceness");
		Files.writeString(executable, "#!/bin/sh\nnice > '" + report + "'\n");
		assertTrue(executable.toFile().setExecutable(true));

		var context = context((request, source) -> executable).toBuilder().processPriority(priority).build();
		try (ExecutionSession session = new LocalExecutionProvider().open(context)) {
			var target = session.prepare(request(priority.name(), JavaRequirement.builder().featureVersion(21).build(), null));
			var process = target.start(JavaCommand.builder().jar(directory.resolve("server.jar")).memoryMegabytes(64).build());
			assertTrue(process.await(Duration.ofSeconds(10)));
		}

		return Integer.parseInt(Files.readString(report).strip());
	}

	private ExecutionContext context(LocalRuntimePreparation runtime) {
		return ExecutionContext.builder().cacheDirectory(directory).localRuntime(runtime)
				.runtimeValidator((properties, request) -> { throw new AssertionError("Local execution must not inspect images"); })
				.imageLocks(path -> { throw new AssertionError("Local execution must not access images"); }).build();
	}

	private ProcessRequest request(String name, JavaRequirement requirement, JavaSource source) {
		return ProcessRequest.builder().name(name).workspace(directory.resolve(name)).javaSelection(JavaSelection.builder().requirement(requirement).source(source).build()).build();
	}
}
