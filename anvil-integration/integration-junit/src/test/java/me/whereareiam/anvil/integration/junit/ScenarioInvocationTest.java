package me.whereareiam.anvil.integration.junit;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
			public @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) { throw start; }
			public void close() { actions.add("engine"); throw close; }
		};

		assertSame(start, assertThrows(RuntimeException.class, () -> new ScenarioInvocation(engine, definition, () -> true)));
		assertEquals(List.of("engine"), actions);
		assertArrayEquals(new Throwable[]{close}, start.getSuppressed());
	}

	@Test
	void transfersOpenResourcesToRegistrationUntilInvocationEnds() {
		List<String> actions = new ArrayList<>();
		var context = new Context(() -> actions.add("context"));
		var invocation = new ScenarioInvocation(engine(context, () -> actions.add("engine")), definition, () -> true);
		var registered = new AtomicReference<ScenarioInvocation>();

		invocation.register(registered::set);
		assertTrue(actions.isEmpty());
		assertEquals(1, context.starts);
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
		var invocation = new ScenarioInvocation(engine(context, () -> { throw engineClose; }), definition, () -> true);

		assertSame(registration, assertThrows(RuntimeException.class,
				() -> invocation.register(ignored -> { throw registration; })));
		assertFalse(context.successful);
		assertArrayEquals(new Throwable[]{contextClose}, registration.getSuppressed());
		assertArrayEquals(new Throwable[]{engineClose}, contextClose.getSuppressed());
	}

	@Test
	void readsTheFinalTestOutcomeWhenTheInvocationCloses() {
		List<String> actions = new ArrayList<>();
		AtomicBoolean successful = new AtomicBoolean(true);
		Context context = new Context(() -> actions.add("context"));
		var invocation = new ScenarioInvocation(engine(context, () -> actions.add("engine")), definition, successful::get);
		assertEquals(1, context.starts);
		successful.set(false);
		invocation.close();
		invocation.close();
		assertFalse(context.successful);
		assertEquals(List.of("context", "engine"), actions);
	}

	@Test
	void contextCleanupFailureRemainsPrimaryWhenEngineCleanupAlsoFails() {
		RuntimeException contextClose = new IllegalStateException("context close");
		RuntimeException engineClose = new IllegalStateException("engine close");
		var invocation = new ScenarioInvocation(engine(new Context(() -> { throw contextClose; }),
				() -> { throw engineClose; }), definition, () -> true);

		assertSame(contextClose, assertThrows(RuntimeException.class, invocation::close));
		assertArrayEquals(new Throwable[]{engineClose}, contextClose.getSuppressed());
	}

	private ScenarioEngine engine(ScenarioContext context, Runnable close) {
		return new ScenarioEngine() {
			public @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) { return context; }
			public void close() { close.run(); }
		};
	}

	@RequiredArgsConstructor
	private final class Context implements ScenarioContext {
		private final Runnable cleanup;
		private int starts;
		private Boolean successful;

		public void start() { starts++; }

		public @NonNull AnvilScenario definition() { return definition; }
		public @NonNull ScenarioProcesses processes() { throw new AssertionError("No process access during cleanup"); }
		public @NonNull PlayerManager players() { throw new AssertionError("No player access during cleanup"); }
		public void finish(boolean successful) { this.successful = successful; cleanup.run(); }
	}
}
