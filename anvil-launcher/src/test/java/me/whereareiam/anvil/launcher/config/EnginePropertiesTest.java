package me.whereareiam.anvil.launcher.config;

import me.whereareiam.anvil.api.model.EngineOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnginePropertiesTest {
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
		setProperty(EngineProperties.PROTOCOL_PROPERTY, "mcprotocol");
		setProperty(EngineProperties.EULA_ACCEPTED_PROPERTY, "true");
		setProperty(EngineProperties.CACHE_DIRECTORY_PROPERTY, "cache");
		setProperty(EngineProperties.WORK_DIRECTORY_PROPERTY, "work");
		setProperty(EngineProperties.javaExecutableProperty(21), "java-21");
		setProperty(EngineProperties.artifactProperty("server"), "server.jar");

		EngineOptions options = EngineProperties.fromSystemProperties();

		assertEquals("mcprotocol", options.getProtocolId());
		assertTrue(options.isEulaAccepted());
		assertEquals(Path.of("cache"), options.getCacheDirectory());
		assertEquals(Path.of("work"), options.getWorkDirectory());
		assertEquals(Path.of("java-21"), options.getJavaExecutables().get(21));
		assertEquals(Path.of("server.jar"), options.getArtifacts().get("server"));
	}

	@Test
	void readsAllOptionsAndJavaVersionsOutsideTheFormerRange() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.javaExecutableProperty(31), "java-31");
		properties.setProperty(EngineProperties.KEEP_FAILED_WORKSPACES_PROPERTY, "false");
		properties.setProperty(EngineProperties.AUTO_DOWNLOAD_JAVA_PROPERTY, "false");
		properties.setProperty(EngineProperties.STOP_TIMEOUT_PROPERTY, "PT7S");
		properties.setProperty(EngineProperties.DEFAULT_JAVA_PROPERTY, "custom-java");
		properties.setProperty(EngineProperties.artifactProperty("plugin"), "plugin.jar");

		EngineOptions options = EngineProperties.from(properties);
		properties.setProperty(EngineProperties.javaExecutableProperty(31), "changed");
		assertEquals(Path.of("java-31"), options.getJavaExecutables().get(31));
		assertFalse(options.isKeepFailedWorkspaces());
		assertFalse(options.isAutoDownloadJavaRuntimes());
		assertEquals(Duration.ofSeconds(7), options.getStopTimeout());
		assertEquals(Path.of("custom-java"), options.getDefaultJavaExecutable());
		assertEquals(Path.of("plugin.jar"), options.getArtifacts().get("plugin"));
		assertThrows(UnsupportedOperationException.class, () -> options.getJavaExecutables().clear());
	}

	@Test
	void rejectsInvalidOverridesInsteadOfIgnoringThem() {
		for (String key : new String[]{"anvil.java.future", "anvil.java.0", "anvil.java.-1"}) {
			Properties properties = new Properties();
			properties.setProperty(key, "java");
			assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(properties));
		}
		for (String duration : new String[]{"PT0S", "-PT1S", "invalid"}) {
			Properties properties = new Properties();
			properties.setProperty(EngineProperties.STOP_TIMEOUT_PROPERTY, duration);
			assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(properties));
		}
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.EULA_ACCEPTED_PROPERTY, "yes");
		assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(properties));
	}

	private void setProperty(String name, String value) {
		originalProperties.putIfAbsent(name, System.getProperty(name));
		System.setProperty(name, value);
	}
}
