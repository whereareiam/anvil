package me.whereareiam.anvil.junit;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioInvocationTest {
	private final AnvilScenario definition = AnvilScenario.builder().name("invocation").entrypoint("server").build();

	@Test
	void closesEngineWhenStartupFailsAndPreservesStartupFailure() {
		List<String> actions = new ArrayList<>();
		RuntimeException start = new IllegalStateException("start");
		RuntimeException close = new IllegalStateException("engine close");
		ScenarioEngine engine = new ScenarioEngine() {
			public ScenarioContext start(AnvilScenario scenario) { throw start; }
			public void close() { actions.add("engine"); throw close; }
		};

		assertSame(start, assertThrows(RuntimeException.class, () -> new ScenarioInvocation(engine, definition)));
		assertEquals(List.of("engine"), actions);
		assertArrayEquals(new Throwable[]{close}, start.getSuppressed());
	}

	@Test
	void transfersOpenResourcesToRegistrationUntilInvocationEnds() {
		List<String> actions = new ArrayList<>();
		var context = new Context(() -> actions.add("context"));
		var invocation = new ScenarioInvocation(engine(context, () -> actions.add("engine")), definition);
		var registered = new AtomicReference<ScenarioInvocation>();

		invocation.register(registered::set);
		assertTrue(actions.isEmpty());
		assertSame(context, registered.get().getContext());
		registered.get().close();
		assertEquals(List.of("context", "engine"), actions);
	}

	@Test
	void registrationFailureClosesBothResourcesAndPreservesAllFailures() {
		RuntimeException registration = new IllegalStateException("registration");
		RuntimeException contextClose = new IllegalStateException("context close");
		RuntimeException engineClose = new IllegalStateException("engine close");
		var context = new Context(() -> { throw contextClose; });
		var invocation = new ScenarioInvocation(engine(context, () -> { throw engineClose; }), definition);

		assertSame(registration, assertThrows(RuntimeException.class,
				() -> invocation.register(ignored -> { throw registration; })));
		assertArrayEquals(new Throwable[]{contextClose}, registration.getSuppressed());
		assertArrayEquals(new Throwable[]{engineClose}, contextClose.getSuppressed());
	}

	@Test
	void contextCleanupFailureRemainsPrimaryWhenEngineCleanupAlsoFails() {
		RuntimeException contextClose = new IllegalStateException("context close");
		RuntimeException engineClose = new IllegalStateException("engine close");
		var invocation = new ScenarioInvocation(engine(new Context(() -> { throw contextClose; }),
				() -> { throw engineClose; }), definition);

		assertSame(contextClose, assertThrows(RuntimeException.class, invocation::close));
		assertArrayEquals(new Throwable[]{engineClose}, contextClose.getSuppressed());
	}

	private ScenarioEngine engine(ScenarioContext context, Runnable close) {
		return new ScenarioEngine() {
			public ScenarioContext start(AnvilScenario scenario) { return context; }
			public void close() { close.run(); }
		};
	}

	@RequiredArgsConstructor
	private final class Context implements ScenarioContext {
		private final Runnable cleanup;

		public AnvilScenario definition() { return definition; }
		public ScenarioProcesses processes() { throw new AssertionError("No process access during cleanup"); }
		public PlayerManager players() { throw new AssertionError("No player access during cleanup"); }
		public void finish(boolean successful) { cleanup.run(); }
	}
}
