package me.whereareiam.anvil.launcher.config;

import me.whereareiam.anvil.api.model.EngineOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EngineDefaultsTest {
	@Test
	void resolvesOnlyOmittedEnvironmentValuesAndPreservesExplicitOptions() {
		EngineOptions requested = EngineOptions.builder().stopTimeout(Duration.ofSeconds(7)).build();
		EngineOptions effective = EngineDefaults.resolve(requested);

		assertNull(requested.getCacheDirectory());
		assertEquals(EngineDefaults.cacheDirectory(), effective.getCacheDirectory());
		assertEquals(Duration.ofSeconds(7), effective.getStopTimeout());

		EngineOptions explicit = requested.toBuilder()
				.cacheDirectory(Path.of("custom-cache"))
				.build();

		EngineOptions resolved = EngineDefaults.resolve(explicit);
		assertEquals(explicit.getCacheDirectory(), resolved.getCacheDirectory());
		assertNotNull(resolved.getParallelism());
		assertNotNull(resolved.getStartupMemoryMegabytes());
		assertNotNull(resolved.getDownloadParallelism());
	}
}
