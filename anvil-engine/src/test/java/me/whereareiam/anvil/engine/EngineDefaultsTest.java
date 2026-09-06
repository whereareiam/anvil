package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.engine.provisioning.java.JavaExecutables;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EngineDefaultsTest {
	@Test
	void resolvesOnlyOmittedEnvironmentValuesAndPreservesExplicitOptions() {
		EngineOptions requested = EngineOptions.builder().stopTimeout(Duration.ofSeconds(7)).build();
		EngineOptions effective = EngineDefaults.resolve(requested);

		assertNull(requested.getCacheDirectory());
		assertNull(requested.getDefaultJavaExecutable());
		assertEquals(EngineDefaults.cacheDirectory(), effective.getCacheDirectory());
		assertEquals(JavaExecutables.current(), effective.getDefaultJavaExecutable());
		assertEquals(Duration.ofSeconds(7), effective.getStopTimeout());

		EngineOptions explicit = requested.toBuilder()
				.cacheDirectory(Path.of("custom-cache"))
				.defaultJavaExecutable(Path.of("custom-java"))
				.build();
		assertEquals(explicit, EngineDefaults.resolve(explicit));
	}
}
