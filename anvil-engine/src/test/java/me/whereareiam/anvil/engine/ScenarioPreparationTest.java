package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioPreparationTest {
	private final List<String> events = new ArrayList<>();

	@Test
	void defersGlobalExtensionsAndSetupUntilFullStartAndExecutesThemOnlyOnce() {
		var executor = new Factory();
		try (var engine = engine(executor)) {
			ScenarioContext prepared = engine.prepare(scenario());
			assertEquals(List.of("prepare"), events);
			prepared.start();
			prepared.start();
			assertEquals(List.of("prepare", "scoped-start", "extension", "setup", "scoped-start"), events);
			prepared.finish(false);
		}
		assertEquals(List.of("prepare", "scoped-start", "extension", "setup", "scoped-start",
				"extension-finish:false", "scoped-finish:false", "shared-close"), events);
	}

	@Test
	void ordinaryStartUsesTheSamePreparationLifecycle() {
		try (var engine = engine(new Factory()); var context = engine.start(scenario())) {
			assertEquals(List.of("prepare", "scoped-start", "extension", "setup"), events);
		}
		assertTrue(events.contains("scoped-finish:true"));
	}

	@Test
	void failedDeferredSetupFinalizesWithFailureAndIsNotRetainedByEngine() {
		var definition = scenario().toBuilder().setupHook(context -> { throw new IllegalStateException("Setup failed"); }).build();
		try (var engine = engine(new Factory())) {
			var prepared = engine.prepare(definition);
			assertThrows(RuntimeException.class, prepared::start);
			assertTrue(events.contains("extension-finish:false"));
			assertTrue(events.contains("scoped-finish:false"));
			assertThrows(IllegalStateException.class, prepared::start);
		}
		assertEquals(1, events.stream().filter("scoped-finish:false"::equals).count());
	}

	private AnvilEngine engine(ScenarioFactory executor) {
		return new AnvilEngine(EngineOptions.builder().eulaAccepted(true).build(), executor,
				List.of(context -> {
					events.add("extension");
					return successful -> events.add("extension-finish:" + successful);
				}), List.of(() -> events.add("shared-close")));
	}

	private AnvilScenario scenario() {
		return AnvilScenario.builder().name("prepared").entrypoint("server")
				.server(MinecraftServer.builder().name("server").platform("paper")
						.distribution(Distribution.remote("1.21.11", "132")).build())
				.setupHook(context -> events.add("setup")).build();
	}

	private final class Factory implements ScenarioFactory {
		@Override
		public @NotNull ScenarioContext create(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) {
			events.add("prepare");
			return new Context(scenario);
		}
	}

	private final class Context implements ScenarioContext {
		private final AnvilScenario scenario;
		private boolean finished;

		private Context(AnvilScenario scenario) { this.scenario = scenario; }

		@Override
		public void start() { events.add("scoped-start"); }

		@Override
		public @NotNull AnvilScenario definition() { return scenario; }

		@Override
		public @NotNull ScenarioProcesses processes() { throw new UnsupportedOperationException("Global lifecycle fixture"); }

		@Override
		public @NotNull PlayerManager players() { throw new UnsupportedOperationException("Global lifecycle fixture"); }

		@Override
		public void finish(boolean successful) {
			if (finished) return;
			finished = true;
			events.add("scoped-finish:" + successful);
		}
	}
}
