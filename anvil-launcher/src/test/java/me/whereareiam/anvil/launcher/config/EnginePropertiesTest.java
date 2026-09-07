package me.whereareiam.anvil.launcher.config;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.net.URI;
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
		setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "21");
		setProperty(EngineProperties.artifactProperty("server"), "server.jar");

		EngineOptions options = EngineProperties.fromSystemProperties();

		assertEquals("mcprotocol", options.getProtocolId());
		assertTrue(options.isEulaAccepted());
		assertEquals(Path.of("cache"), options.getCacheDirectory());
		assertEquals(Path.of("work"), options.getWorkDirectory());
		assertEquals(21, options.getJavaRequirement().getFeatureVersion());
		assertEquals(Path.of("server.jar"), options.getArtifacts().get("server"));
	}

	@Test
	void readsAllOptionsAndJavaVersionsOutsideTheFormerRange() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "31");
		properties.setProperty(EngineProperties.KEEP_FAILED_WORKSPACES_PROPERTY, "false");
		properties.setProperty(EngineProperties.AUTO_DOWNLOAD_JAVA_PROPERTY, "false");
		properties.setProperty(EngineProperties.STOP_TIMEOUT_PROPERTY, "PT7S");
		properties.setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "32");
		properties.setProperty(EngineProperties.artifactProperty("plugin"), "plugin.jar");

		EngineOptions options = EngineProperties.from(properties);
		properties.setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "32");
		assertEquals(32, options.getJavaRequirement().getFeatureVersion());
		assertFalse(options.isKeepFailedWorkspaces());
		assertFalse(options.isDownloadJava());
		assertEquals(Duration.ofSeconds(7), options.getStopTimeout());
		assertEquals(32, options.getJavaRequirement().getFeatureVersion());
		assertEquals(Path.of("plugin.jar"), options.getArtifacts().get("plugin"));
		assertThrows(UnsupportedOperationException.class, () -> options.getArtifacts().clear());
	}

	@Test
	void mapsAnExplicitJavaExecutableSource() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.JAVA_EXECUTABLE_PROPERTY, "/opt/jdk-25/bin/java");

		EngineOptions options = EngineProperties.from(properties);

		assertEquals(new LocalJavaExecutable(Path.of("/opt/jdk-25/bin/java")), options.getJavaSource());
	}

	@Test
	void mapsAndValidatesAnExplicitJavaArchiveSource() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.JAVA_ARCHIVE_URI_PROPERTY, "https://example.test/jdk.tar.gz");
		properties.setProperty(EngineProperties.JAVA_ARCHIVE_SHA256_PROPERTY, "a".repeat(64));

		EngineOptions options = EngineProperties.from(properties);

		assertEquals(JavaArchive.builder().uri(URI.create("https://example.test/jdk.tar.gz")).sha256("a".repeat(64)).build(), options.getJavaSource());
		properties.remove(EngineProperties.JAVA_ARCHIVE_SHA256_PROPERTY);
		assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(properties));
	}

	@Test
	void rejectsInvalidOverridesInsteadOfIgnoringThem() {
		for (String value : new String[]{"future", "0", "-1"}) {
			Properties properties = new Properties();
			properties.setProperty(EngineProperties.JAVA_VERSION_PROPERTY, value);
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
