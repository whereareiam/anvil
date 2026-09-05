package me.whereareiam.anvil.engine.provisioning;

import me.whereareiam.anvil.engine.AnvilException;
import me.whereareiam.anvil.engine.model.EngineOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessJavaResolverTest {
	@Test
	void reusesTheCurrentJvmOnlyInsideTheSupportedRange() {
		int current = Runtime.version().feature();
		Path java = Path.of("configured-current-java");
		var resolver = resolver(EngineOptions.builder().defaultJavaExecutable(java).build());
		assertEquals(java, resolver.resolve(current, current));
	}

	@Test
	void prefersAnExplicitJava17InstallationForLegacyDistributions() {
		Path java17 = Path.of("configured-java-17");
		var resolver = resolver(EngineOptions.builder().javaExecutable(17, java17).build());
		assertEquals(java17, resolver.resolve(17, 18));
	}

	@Test
	void rejectsContradictoryProviderBoundsBeforeResolvingAJavaInstallation() {
		assertThrows(AnvilException.class, () -> resolver(EngineOptions.builder().build()).resolve(21, 20));
	}

	private ProcessJavaResolver resolver(EngineOptions options) {
		return new ProcessJavaResolver(options, new TemurinRuntimeProvisioner(new DownloadCache()));
	}
}
