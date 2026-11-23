package me.whereareiam.anvil.engine.config;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.type.ProcessPriority;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EngineDefaultsTest {
	@Test
	void resolvesOnlyOmittedEnvironmentValuesAndPreservesExplicitOptions() {
		EngineOptions requested = EngineOptions.builder()
				.processTimeouts(ProcessTimeouts.builder().shutdown(Duration.ofSeconds(7)).build())
				.build();
		EngineOptions effective = EngineDefaults.resolve(requested);

		assertNull(requested.cacheDirectory());
		assertNull(requested.processTimeouts().startup());
		assertEquals(Duration.ofMinutes(2), effective.processTimeouts().startup());
		assertEquals(EngineDefaults.cacheDirectory(), effective.cacheDirectory());
		assertEquals(Duration.ofSeconds(7), effective.processTimeouts().shutdown());

		EngineOptions explicit = requested.toBuilder()
				.cacheDirectory(Path.of("custom-cache"))
				.processScheduling(ProcessScheduling.builder().parallelism(2).processors(4).build())
				.build();

		EngineOptions resolved = EngineDefaults.resolve(explicit);
		assertEquals(explicit.cacheDirectory(), resolved.cacheDirectory());
		assertEquals(2, resolved.processScheduling().parallelism());
		assertNull(explicit.processScheduling().startupMemoryMegabytes());
		assertNotNull(resolved.processScheduling().startupMemoryMegabytes());
		assertNotNull(resolved.downloadParallelism());
		assertEquals(4, resolved.processScheduling().processors(), "An explicit limit survives resolution");
		assertNull(effective.processScheduling().processors(), "Processes assume every processor unless limited");
		assertEquals(ProcessPriority.NORMAL, effective.processScheduling().priority());
	}
}
