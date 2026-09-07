package me.whereareiam.anvil.execution.local;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.execution.api.model.ProcessRequest;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import me.whereareiam.anvil.provisioning.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalExecutionSessionTest {
	@TempDir
	Path directory;

	@Test
	void resolvesJavaRequirementsIndependentlyForEachProcess() {
		List<JavaRequirement> requirements = new ArrayList<>();
		JavaProvisioner java = new JavaProvisioner() {
			@Override
			public @NotNull Path resolve(@NotNull JavaRequirement requirement, int minimumVersion) {
				requirements.add(requirement);
				return Path.of("java-" + requirement.getDistribution());
			}

			@Override
			public @NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
				return JavaInstallation.builder().executable(executable).featureVersion(21).version("21")
						.runtimeVersion("21").vendor("test").virtualMachine("test").build();
			}

			@Override
			public void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement, int minimumVersion) { }
		};
		try (var artifacts = new me.whereareiam.anvil.provisioning.cache.ArtifactCache(directory, true, false, 1)) {
			ExecutionContext context = ExecutionContext.builder().cacheDirectory(directory).bindAddress("127.0.0.1")
					.javaValidator(java).artifacts(artifacts).build();
		LocalExecutionSession session = new LocalExecutionSession(context, LocalExecutionSettings.builder().build(), java);
		JavaRequirement temurin = JavaRequirement.builder().distribution("temurin").featureVersion(21).build();
		JavaRequirement graal = JavaRequirement.builder().distribution("graalvm-community").featureVersion(21).build();

		session.prepare(ProcessRequest.builder().name("temurin").workspace(directory.resolve("a"))
				.javaRequirement(temurin).minimumJavaVersion(21).build());
		session.prepare(ProcessRequest.builder().name("graal").workspace(directory.resolve("b"))
				.javaRequirement(graal).minimumJavaVersion(21).build());

			assertEquals(List.of(temurin, graal), requirements);
		}
	}

	@Test
	void prefersTheRequestSourceOverProviderDefaults() {
		List<JavaSource> sources = new ArrayList<>();
		JavaProvisioner java = new JavaProvisioner() {
			@Override
			public @NotNull Path resolve(@NotNull JavaRequirement requirement, int minimumVersion) {
				return Path.of("default-java");
			}

			@Override
			public @NotNull Path resolve(@NotNull JavaRequirement requirement, int minimumVersion, JavaSource source) {
				sources.add(source);
				return Path.of("explicit-java");
			}

			@Override
			public @NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
				return JavaInstallation.builder().executable(executable).featureVersion(21).version("21")
						.runtimeVersion("21").vendor("test").virtualMachine("test").build();
			}

			@Override
			public void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement, int minimumVersion) { }
		};
		try (var artifacts = new me.whereareiam.anvil.provisioning.cache.ArtifactCache(directory, true, false, 1)) {
			ExecutionContext context = ExecutionContext.builder().cacheDirectory(directory).bindAddress("127.0.0.1")
					.javaValidator(java).artifacts(artifacts).build();
			LocalExecutionSession session = new LocalExecutionSession(context, LocalExecutionSettings.builder()
					.javaHome("default:21", directory.resolve("provider-jdk")).build(), java);
			JavaSource explicit = JavaSource.executable(directory.resolve("explicit/bin/java"));

			session.prepare(ProcessRequest.builder().name("explicit").workspace(directory.resolve("work"))
					.javaRequirement(JavaRequirement.builder().featureVersion(21).build())
					.javaSource(explicit).minimumJavaVersion(21).build());

			assertEquals(List.of(explicit), sources);
		}
	}
}
