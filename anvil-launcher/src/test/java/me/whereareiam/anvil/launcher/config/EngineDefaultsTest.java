package me.whereareiam.anvil.launcher.config;

import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EngineDefaultsTest {
	@Test
	void resolvesOnlyOmittedEnvironmentValuesAndPreservesExplicitOptions() {
		EngineOptions requested = EngineOptions.builder().processTimeouts(ProcessTimeouts.builder().shutdown(Duration.ofSeconds(7)).build()).build();
		EngineOptions effective = EngineDefaults.resolve(requested);

		assertNull(requested.getCacheDirectory());
		assertNull(requested.getProcessTimeouts().getStartup());
		assertEquals(Duration.ofMinutes(2), effective.getProcessTimeouts().getStartup());
		assertEquals(EngineDefaults.cacheDirectory(), effective.getCacheDirectory());
		assertEquals(Duration.ofSeconds(7), effective.getProcessTimeouts().getShutdown());

		EngineOptions explicit = requested.toBuilder()
				.cacheDirectory(Path.of("custom-cache"))
				.processScheduling(ProcessScheduling.builder().parallelism(2).build())
				.build();

		EngineOptions resolved = EngineDefaults.resolve(explicit);
		assertEquals(explicit.getCacheDirectory(), resolved.getCacheDirectory());
		assertEquals(2, resolved.getProcessScheduling().getParallelism());
		assertNull(explicit.getProcessScheduling().getStartupMemoryMegabytes());
		assertNotNull(resolved.getProcessScheduling().getStartupMemoryMegabytes());
		assertNotNull(resolved.getDownloadParallelism());
	}
}
