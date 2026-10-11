package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.engine.config.EngineProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilToolingTest {
	@Test
	void requestsColorsForTheIdeWithoutChangingCallerOrGlobalProperties() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.PROTOCOL_LIBRARY_PROPERTY, "fixture");
		properties.setProperty(EngineProperties.artifactProperty("plugin"), "plugin.jar");
		String global = System.getProperty(EngineProperties.CONSOLE_COLORS_PROPERTY);

		EngineOptions options = AnvilTooling.engineOptions(properties);

		assertTrue(options.isConsoleColors());
		assertEquals("fixture", options.getProtocolLibrary());
		assertEquals(Path.of("plugin.jar"), options.getArtifacts().get("plugin"));
		assertNull(properties.getProperty(EngineProperties.CONSOLE_COLORS_PROPERTY));
		assertEquals(global, System.getProperty(EngineProperties.CONSOLE_COLORS_PROPERTY));
		assertFalse(EngineProperties.from(properties).isConsoleColors(), "Direct/JUnit property decoding keeps its own default");
	}

	@Test
	void respectsExplicitFalseIncludingInheritedPropertyDefaults() {
		Properties defaults = new Properties();
		defaults.setProperty(EngineProperties.CONSOLE_COLORS_PROPERTY, "false");
		Properties properties = new Properties(defaults);
		assertFalse(AnvilTooling.engineOptions(properties).isConsoleColors());
		properties.setProperty(EngineProperties.CONSOLE_COLORS_PROPERTY, "FALSE");
		assertFalse(AnvilTooling.engineOptions(properties).isConsoleColors());
		properties.setProperty(EngineProperties.CONSOLE_COLORS_PROPERTY, "true");
		assertTrue(AnvilTooling.engineOptions(properties).isConsoleColors());
		assertEquals("false", defaults.getProperty(EngineProperties.CONSOLE_COLORS_PROPERTY));
	}

	@Test
	void doesNotReplaceInvalidExplicitColorSettingsWithTheIdeDefault() {
		Properties properties = new Properties();
		properties.setProperty(EngineProperties.CONSOLE_COLORS_PROPERTY, "automatic");
		assertThrows(IllegalArgumentException.class, () -> AnvilTooling.engineOptions(properties));
		assertEquals("automatic", properties.getProperty(EngineProperties.CONSOLE_COLORS_PROPERTY));
	}
}
