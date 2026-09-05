package me.whereareiam.anvil.engine.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EngineOptionsTest {
	private final Map<String, String> originalProperties = new LinkedHashMap<>();

	@AfterEach
	void restoreSystemProperties() {
		originalProperties.forEach((name, value) -> {
			if (value == null)
				System.clearProperty(name);
			else
				System.setProperty(name, value);
		});
	}

	@Test
	void mapsRuntimePropertiesThroughOneSharedContract() {
		setProperty(EngineOptions.PROTOCOL_PROPERTY, "mcprotocol");
		setProperty(EngineOptions.EULA_ACCEPTED_PROPERTY, "true");
		setProperty(EngineOptions.CACHE_DIRECTORY_PROPERTY, "cache");
		setProperty(EngineOptions.WORK_DIRECTORY_PROPERTY, "work");
		setProperty(EngineOptions.javaExecutableProperty(21), "java-21");
		setProperty(EngineOptions.artifactProperty("server"), "server.jar");

		EngineOptions options = EngineOptions.fromSystemProperties();

		assertEquals("mcprotocol", options.getProtocolId());
		assertTrue(options.isEulaAccepted());
		assertEquals(Path.of("cache"), options.getCacheDirectory());
		assertEquals(Path.of("work"), options.getWorkDirectory());
		assertEquals(Path.of("java-21"), options.getJavaExecutables().get(21));
		assertEquals(Path.of("server.jar"), options.getArtifacts().get("server"));
	}

	private void setProperty(String name, String value) {
		originalProperties.putIfAbsent(name, System.getProperty(name));
		System.setProperty(name, value);
	}
}
