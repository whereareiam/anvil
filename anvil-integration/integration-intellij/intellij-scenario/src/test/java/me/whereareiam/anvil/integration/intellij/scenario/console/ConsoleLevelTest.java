package me.whereareiam.anvil.integration.intellij.scenario.console;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleLevelTest {
	@Test
	void readsTheLevelsServerAndProxyLoggersPrint() {
		assertEquals(ConsoleLevel.INFO, ConsoleLevel.declared("[12:00:00 INFO]: Done (1.2s)!"));
		assertEquals(ConsoleLevel.WARNING, ConsoleLevel.declared("[12:00:00 WARN]: Can't keep up!"));
		assertEquals(ConsoleLevel.ERROR, ConsoleLevel.declared("[12:00:00] [Server thread/ERROR]: Could not pass event"));
		assertEquals(ConsoleLevel.ERROR, ConsoleLevel.declared("[12:00:00 SEVERE]: Legacy logger failure"));
		assertEquals(ConsoleLevel.INFO, ConsoleLevel.declared("[12:00:00 DEBUG]: Verbose"));
		assertEquals(ConsoleLevel.WARNING, ConsoleLevel.declared("\u001B[33m[12:00:00 WARN]: Coloured\u001B[0m"));
		assertNull(ConsoleLevel.declared("Player said [ERROR] in chat later on"));
	}

	@Test
	void undeclaredLinesContinueTheirChannelOrFollowTheirStream() {
		assertEquals(ConsoleLevel.ERROR, ConsoleLevel.of("\tat example.Listener.onJoin(Listener.java:12)", ConsoleLevel.ERROR, false));
		assertEquals(ConsoleLevel.ERROR, ConsoleLevel.of("Caused by: java.lang.IllegalStateException", ConsoleLevel.ERROR, false));
		assertEquals(ConsoleLevel.ERROR, ConsoleLevel.of("Exception in thread main", null, true));
		assertEquals(ConsoleLevel.INFO, ConsoleLevel.of("Loading libraries, please wait...", null, false));
	}

	@Test
	void filtersShowTheirLevelAndAnythingMoreSevere() {
		assertTrue(ConsoleLevel.ERROR.shownAt(ConsoleLevel.WARNING));
		assertFalse(ConsoleLevel.INFO.shownAt(ConsoleLevel.WARNING));
		assertTrue(ConsoleLevel.INFO.shownAt(ConsoleLevel.INFO));
	}
}
