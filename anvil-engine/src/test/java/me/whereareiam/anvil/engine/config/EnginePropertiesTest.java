package me.whereareiam.anvil.engine.config;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.api.type.SupportPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
	void decodesProtocolLibrarySupportPolicyAndAdditionalReleases() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.PROTOCOL_LIBRARY_PROPERTY, "mcprotocol");
		properties.setProperty(EngineProperties.SUPPORT_POLICY_PROPERTY, "Strict");
		properties.setProperty(EngineProperties.protocolReleasesProperty("mcprotocol"), "releases.toml");

		EngineOptions options = EngineProperties.from(properties);

		assertEquals("mcprotocol", options.protocolLibrary());
		assertEquals(SupportPolicy.STRICT, options.supportPolicy());
		assertEquals(Map.of("mcprotocol", Path.of("releases.toml")), options.protocolReleases());
	}

	@Test
	void leavesProcessorsUnlimitedAndPriorityNormalUnlessConfigured() {
		assertNull(EngineProperties.from(new Properties()).processScheduling().processors());
		assertEquals(ProcessPriority.NORMAL, EngineProperties.from(new Properties()).processScheduling().priority());

		Properties properties = new Properties();
		properties.setProperty(EngineProperties.PROCESSORS_PROPERTY, "4");
		properties.setProperty(EngineProperties.PROCESS_PRIORITY_PROPERTY, "Low");
		var scheduling = EngineProperties.from(properties).processScheduling();
		assertEquals(4, scheduling.processors());
		assertEquals(ProcessPriority.LOW, scheduling.priority());
	}

	@Test
	void rejectsInvalidProcessorAndPriorityLimits() {
		Properties processors = new Properties();
		processors.setProperty(EngineProperties.PROCESSORS_PROPERTY, "0");
		assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(processors));

		Properties priority = new Properties();
		priority.setProperty(EngineProperties.PROCESS_PRIORITY_PROPERTY, "idle");
		assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(priority));
	}

	@Test
	void defaultsToLenientSupportAndRejectsUnknownPolicies() {
		assertEquals(SupportPolicy.LENIENT, EngineProperties.from(new Properties()).supportPolicy());

		Properties properties = new Properties();
		properties.setProperty(EngineProperties.SUPPORT_POLICY_PROPERTY, "relaxed");
		assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(properties));
	}

	@Test
	void mapsRuntimePropertiesThroughOneSharedContract() {
		setProperty(EngineProperties.PROTOCOL_LIBRARY_PROPERTY, "mcprotocol");
		setProperty(EngineProperties.EULA_ACCEPTED_PROPERTY, "true");
		setProperty(EngineProperties.CACHE_DIRECTORY_PROPERTY, "cache");
		setProperty(EngineProperties.WORK_DIRECTORY_PROPERTY, "work");
		setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "21");
		setProperty(EngineProperties.artifactProperty("server"), "server.jar");

		EngineOptions options = EngineProperties.fromSystemProperties();

		assertEquals("mcprotocol", options.protocolLibrary());
		assertTrue(options.eulaAccepted());
		assertEquals(Path.of("cache"), options.cacheDirectory());
		assertEquals(Path.of("work"), options.workDirectory());
		assertEquals(21, options.javaSelection().requirement().featureVersion());
		assertEquals(Path.of("server.jar"), options.artifacts().get("server"));
	}

	@Test
	void readsAllOptionsAndJavaVersionsOutsideTheFormerRange() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "31");
		properties.setProperty(EngineProperties.KEEP_FAILED_WORKSPACES_PROPERTY, "false");
		properties.setProperty(EngineProperties.AUTO_DOWNLOAD_JAVA_PROPERTY, "false");
		properties.setProperty(EngineProperties.STOP_TIMEOUT_PROPERTY, "PT7S");
		properties.setProperty(EngineProperties.STARTUP_TIMEOUT_PROPERTY, "PT3M");
		properties.setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "32");
		properties.setProperty(EngineProperties.artifactProperty("plugin"), "plugin.jar");

		EngineOptions options = EngineProperties.from(properties);
		properties.setProperty(EngineProperties.JAVA_VERSION_PROPERTY, "32");
		assertEquals(32, options.javaSelection().requirement().featureVersion());
		assertFalse(options.keepFailedWorkspaces());
		assertFalse(options.downloadJava());
		assertEquals(Duration.ofSeconds(7), options.processTimeouts().shutdown());
		assertEquals(Duration.ofMinutes(3), options.processTimeouts().startup());
		assertEquals(32, options.javaSelection().requirement().featureVersion());
		assertEquals(Path.of("plugin.jar"), options.artifacts().get("plugin"));
		assertThrows(UnsupportedOperationException.class, () -> options.artifacts().clear());
	}

	@Test
	void mapsAnExplicitJavaExecutableSource() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.JAVA_EXECUTABLE_PROPERTY, "/opt/jdk-25/bin/java");

		EngineOptions options = EngineProperties.from(properties);

		assertEquals(new LocalJavaExecutable(Path.of("/opt/jdk-25/bin/java")), options.javaSelection().source());
	}

	@Test
	void mapsAndValidatesAnExplicitJavaArchiveSource() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.JAVA_ARCHIVE_URI_PROPERTY, "https://example.test/jdk.tar.gz");
		properties.setProperty(EngineProperties.JAVA_ARCHIVE_SHA256_PROPERTY, "a".repeat(64));

		EngineOptions options = EngineProperties.from(properties);

		assertEquals(
				JavaArchive.builder().uri(URI.create("https://example.test/jdk.tar.gz")).sha256("a".repeat(64)).build(),
				options.javaSelection().source()
		);
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

	@Test
	void consoleColorsDefaultToPlatformBehaviorAndDecodeExplicitRequests() {
		assertFalse(EngineOptions.builder().build().consoleColors());
		assertFalse(EngineProperties.from(new Properties()).consoleColors());
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.CONSOLE_COLORS_PROPERTY, "true");
		assertTrue(EngineProperties.from(properties).consoleColors());
		properties.setProperty(EngineProperties.CONSOLE_COLORS_PROPERTY, "FALSE");
		assertFalse(EngineProperties.from(properties).consoleColors());
		assertEquals("FALSE", properties.getProperty(EngineProperties.CONSOLE_COLORS_PROPERTY));
	}

	@Test
	void rejectsInvalidConsoleColorProperties() {
		for (String value : new String[]{"yes", "1", "", " true "}) {
			Properties properties = new Properties();
			properties.setProperty(EngineProperties.CONSOLE_COLORS_PROPERTY, value);
			IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> EngineProperties.from(properties));
			assertEquals("anvil.console.colors must be true or false", failure.getMessage());
		}
	}

	private void setProperty(String name, String value) {
		originalProperties.putIfAbsent(name, System.getProperty(name));
		System.setProperty(name, value);
	}
}
