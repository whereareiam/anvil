package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class AnvilRunnerTest {
	@Test
	void listsScenariosWithoutCreatingAnEngine() throws Exception {
		StringWriter buffer = new StringWriter();
		new AnvilRunner(new StringReader(""), new PrintWriter(new BufferedWriter(buffer)), () -> {
			throw new AssertionError("Listing must not acquire an engine");
		}).run(new String[]{"--list"});

		String output = buffer.toString();
		assertTrue(output.contains("Demo (demo)"), output);
		assertTrue(output.contains(TestScenario.class.getName()), output);
	}

	@Test
	void usesTheSuppliedEngineAndClosesItAfterFailedStartupWithoutDefaultLauncherAvailable() {
		assertThrows(ClassNotFoundException.class, () -> Class.forName("me.whereareiam.anvil.launcher.AnvilLauncher"));
		assertThrows(ClassNotFoundException.class, () -> Class.forName("me.whereareiam.anvil.engine.AnvilEngine"));
		AtomicInteger creates = new AtomicInteger();
		AtomicInteger closes = new AtomicInteger();
		IllegalStateException failure = new IllegalStateException("Host-owned engine failure");
		ScenarioEngine engine = new ScenarioEngine() {
			@Override
			public @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) {
				assertEquals("demo", scenario.getName());
				throw failure;
			}

			@Override
			public void close() {
				closes.incrementAndGet();
			}
		};
		AnvilRunner runner = new AnvilRunner(new StringReader(""), new PrintWriter(new StringWriter()), () -> {
			creates.incrementAndGet();
			return engine;
		});
		assertSame(failure, assertThrows(IllegalStateException.class,
				() -> runner.run(new String[]{"--definition=" + TestScenario.class.getName()})));
		assertEquals(1, creates.get());
		assertEquals(1, closes.get());
	}

	@Test
	void rejectsUnknownOptionsBeforeLoadingAProviderOrCreatingAnEngine() {
		AnvilRunner runner = new AnvilRunner(new StringReader(""), new PrintWriter(new StringWriter()), () -> {
			throw new AssertionError("Invalid arguments must not acquire an engine");
		});
		assertThrows(IllegalArgumentException.class,
				() -> runner.run(new String[]{"--unknown", "--list"}));
	}

	public static final class TestScenario implements AnvilScenarioDefinition {
		@Override
		public AnvilScenario define() {
			return AnvilScenario.builder()
					.name("demo")
					.entrypoint("server")
					.manual(true)
					.build();
		}
	}
}
