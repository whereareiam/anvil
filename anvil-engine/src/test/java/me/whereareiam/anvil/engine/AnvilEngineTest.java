package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioExtension;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import me.whereareiam.anvil.api.scenario.ScenarioHook;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AnvilEngineTest {
	private final List<String> events = new CopyOnWriteArrayList<>();

	@Test
	void installsScenarioExtensionsBeforeSetupAndClosesThemBeforeTheScopedContext() {
		var engine = engine((scenario, observer) -> new Context(scenario),
				scenario -> { events.add("attach:first"); return successful -> events.add("first:" + successful); },
				scenario -> { events.add("attach:second"); return successful -> events.add("second:" + successful); });
		var context = engine.start(scenario(run -> events.add("setup")));
		assertEquals(List.of("attach:first", "attach:second", "setup"), events);

		context.close();
		engine.close();

		assertEquals(List.of("attach:first", "attach:second", "setup", "second:true", "first:true", "context:true", "shared"), events);
		assertThrows(IllegalStateException.class, () -> engine.start(scenario(null)));
	}

	@Test
	void passesCallerFailureToEveryContributionAndTheUnderlyingContext() {
		var engine = engine((scenario, observer) -> new Context(scenario),
				scenario -> successful -> events.add("extension:" + successful));
		var context = engine.start(scenario(null));

		context.finish(false);
		engine.close();

		assertEquals(List.of("extension:false", "context:false", "shared"), events);
	}

	@Test
	void aCleanupFailureChangesTheOutcomeForRemainingAttachmentsAndTheContext() {
		IllegalStateException failure = new IllegalStateException("second attachment cleanup");
		var engine = engine((scenario, observer) -> new Context(scenario),
				scenario -> successful -> events.add("first:" + successful),
				scenario -> successful -> {
					events.add("second:" + successful);
					throw failure;
				});
		var context = engine.start(scenario(null));

		assertSame(failure, assertThrows(IllegalStateException.class, context::close));
		engine.close();

		assertEquals(List.of("second:true", "first:false", "context:false", "shared"), events);
	}

	@Test
	void preservesSetupFailureAndSuppressesEveryCleanupFailure() {
		AssertionError setup = new AssertionError("setup");
		IllegalStateException attachmentCleanup = new IllegalStateException("attachment cleanup");
		IllegalArgumentException contextCleanup = new IllegalArgumentException("context cleanup");
		var engine = engine((scenario, observer) -> {
			Context context = new Context(scenario);
			context.failure = contextCleanup;
			return context;
		}, scenario -> successful -> {
			events.add("attachment:" + successful);
			throw attachmentCleanup;
		});

		assertSame(setup, assertThrows(AssertionError.class, () -> engine.start(scenario(run -> { throw setup; }))));
		assertEquals(List.of("attachment:false", "context:false"), events);
		assertSame(attachmentCleanup, setup.getSuppressed()[0]);
		assertSame(contextCleanup, attachmentCleanup.getSuppressed()[0]);
		engine.close();
		assertEquals(3, events.size(), "Failed startup must not retain a completed scenario");
	}

	@Test
	void rollsBackEarlierAttachmentsWhenALaterExtensionCannotAttach() {
		IllegalStateException attachment = new IllegalStateException("attach failed");
		var engine = engine((scenario, observer) -> new Context(scenario),
				scenario -> successful -> events.add("first:" + successful),
				scenario -> { throw attachment; });

		assertSame(attachment, assertThrows(IllegalStateException.class, () -> engine.start(scenario(run -> fail("Setup must not run")))));
		engine.close();

		assertEquals(List.of("first:false", "context:false", "shared"), events);
	}

	@Test
	void finalizesAnAttachmentReturnedAfterItsExtensionClosedTheContext() {
		var engine = engine((scenario, observer) -> new Context(scenario), scenario -> {
			scenario.finish(false);
			return successful -> events.add("late:" + successful);
		});

		assertThrows(IllegalStateException.class, () -> engine.start(scenario(run -> fail("Setup must not run"))));
		engine.close();

		assertEquals(List.of("context:false", "late:false", "shared"), events);
	}

	@Test
	void rejectsClosingTheParentEngineFromSetupUntilTheCurrentContextIsFinalized() {
		AtomicReference<ScenarioEngine> owner = new AtomicReference<>();
		var engine = engine((scenario, observer) -> new Context(scenario),
				scenario -> successful -> events.add("attachment:" + successful));
		owner.set(engine);

		var failure = assertThrows(ScenarioStartupException.class, () -> engine.start(scenario(run -> owner.get().close())));
		assertInstanceOf(IllegalStateException.class, failure.getCause());

		assertEquals(List.of("attachment:false", "context:false"), events);
		engine.close();
		assertEquals(List.of("attachment:false", "context:false", "shared"), events);
	}

	@Test
	void rejectsClosingTheParentEngineFromAnExtensionBeforeAttachmentTransfer() {
		AtomicReference<ScenarioEngine> owner = new AtomicReference<>();
		var engine = engine((scenario, observer) -> new Context(scenario),
				scenario -> successful -> events.add("first:" + successful),
				scenario -> {
					owner.get().close();
					throw new AssertionError("Closing must fail before acquiring more extension resources");
				});
		owner.set(engine);

		assertThrows(IllegalStateException.class, () -> engine.start(scenario(null)));

		assertEquals(List.of("first:false", "context:false"), events);
		engine.close();
		assertEquals(List.of("first:false", "context:false", "shared"), events);
	}

	@Test
	void keepsTheOuterStartupProtectedAfterANestedScenarioHasStarted() {
		AtomicReference<ScenarioEngine> owner = new AtomicReference<>();
		var engine = engine((scenario, observer) -> new Context(scenario));
		owner.set(engine);

		var failure = assertThrows(ScenarioStartupException.class, () -> engine.start(scenario(run -> {
			owner.get().start(scenario(null)).close();
			owner.get().close();
		})));
		assertInstanceOf(IllegalStateException.class, failure.getCause());
		assertEquals(List.of("context:true", "context:false"), events);
		engine.close();
		assertEquals(List.of("context:true", "context:false", "shared"), events);
	}

	@Test
	void aSetupHookMayFinishItsContextWithoutRetainingIt() {
		var engine = engine((scenario, observer) -> new Context(scenario));

		engine.start(scenario(run -> ((ScenarioContext) run).close()));
		engine.close();

		assertEquals(List.of("context:true", "shared"), events);
	}

	@Test
	void rejectsInvalidStructureBeforeOpeningScopedServices() {
		var engine = engine((scenario, observer) -> { throw new AssertionError("Scoped execution must not run"); });
		assertThrows(ScenarioValidationException.class, () -> engine.start(scenario(null).toBuilder().entrypoint("missing").build()));
		engine.close();
		assertEquals(List.of("shared"), events);
	}

	@Test
	void scopedStartupFailureDoesNotPreventSharedCleanup() {
		IllegalStateException failure = new IllegalStateException("executor rolled back startup");
		var engine = engine((scenario, observer) -> { throw failure; });
		assertSame(failure, assertThrows(IllegalStateException.class, () -> engine.start(scenario(null))));
		engine.close();
		assertEquals(List.of("shared"), events);
	}

	@Test
	void attemptsEverySessionBeforeClosingSharedResources() {
		List<Context> contexts = new ArrayList<>();
		var engine = engine((scenario, observer) -> {
			Context context = new Context(scenario);
			contexts.add(context);
			return context;
		});
		engine.start(scenario(null));
		engine.start(scenario(null));
		IllegalStateException failure = new IllegalStateException("first context cleanup");
		contexts.getFirst().failure = failure;

		assertSame(failure, assertThrows(IllegalStateException.class, engine::close));
		engine.close();

		assertEquals(List.of("context:true", "context:true", "shared"), events);
	}

	@Test
	void engineClosureWaitsForConcurrentContextCleanupWithoutHoldingTheEngineMonitor() throws Exception {
		CountDownLatch closing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<ScenarioEngine> owner = new AtomicReference<>();
		var engine = engine((scenario, observer) -> new Context(scenario), scenario -> successful -> {
			closing.countDown();
			await(release);
			assertThrows(IllegalStateException.class, () -> owner.get().start(scenario(null)));
			events.add("extension-finished");
		});
		owner.set(engine);
		var context = engine.start(scenario(null));
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var contextClose = tasks.submit(context::close);
			assertTrue(closing.await(5, TimeUnit.SECONDS));
			var engineClose = tasks.submit(engine::close);
			try {
				assertThrows(TimeoutException.class, () -> engineClose.get(100, TimeUnit.MILLISECONDS));
				assertFalse(events.contains("shared"));
			} finally {
				release.countDown();
			}
			contextClose.get(5, TimeUnit.SECONDS);
			engineClose.get(5, TimeUnit.SECONDS);
		}
		assertEquals(List.of("extension-finished", "context:true", "shared"), events);
	}

	@Test
	@Timeout(10)
	void rejectsFinishingTheScenarioOrClosingTheEngineFromAProcessObserver() {
		AtomicReference<ScenarioEngine> owner = new AtomicReference<>();
		AtomicReference<ScenarioContext> prepared = new AtomicReference<>();
		List<Throwable> rejected = new ArrayList<>();
		var engine = engine((scenario, observer) -> {
			Context context = new Context(scenario);
			RunningProcess process = (RunningProcess) Proxy.newProxyInstance(
					RunningProcess.class.getClassLoader(),
					new Class<?>[]{RunningProcess.class},
					(proxy, method, arguments) -> { throw new UnsupportedOperationException(); }
			);
			context.onStart = () -> observer.processCreated(process);
			return context;
		});
		owner.set(engine);
		ScenarioObserver observer = process -> {
			for (Runnable action : List.<Runnable>of(() -> prepared.get().finish(false), () -> owner.get().close())) {
				try {
					action.run();
				} catch (IllegalStateException failure) {
					rejected.add(failure);
				}
			}
		};

		prepared.set(engine.prepare(scenario(null), observer));
		prepared.get().start();
		assertEquals(2, rejected.size());
		assertEquals(List.of(), events);

		prepared.get().close();
		engine.close();
		assertEquals(List.of("context:true", "shared"), events);
	}

	@Test
	@Timeout(10)
	void anotherThreadCanFinishAScenarioWhileItsStartupIsBlocked() throws Exception {
		CountDownLatch starting = new CountDownLatch(1);
		CountDownLatch released = new CountDownLatch(1);
		var engine = engine((scenario, observer) -> {
			Context context = new Context(scenario) {
				@Override
				public void finish(boolean successful) {
					released.countDown();
					super.finish(successful);
				}
			};
			context.onStart = () -> {
				starting.countDown();
				await(released);
			};
			return context;
		});
		var context = engine.prepare(scenario(null), null);

		try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var start = tasks.submit(context::start);
			assertTrue(starting.await(5, TimeUnit.SECONDS));

			context.finish(false);
			assertThrows(ExecutionException.class, () -> start.get(5, TimeUnit.SECONDS));
		}
		engine.close();
		assertEquals(List.of("context:false", "shared"), events);
	}

	private ScenarioEngine engine(ScenarioFactory executor, ScenarioExtension... extensions) {
		return new AnvilEngineBuilder(executor).options(EngineOptions.builder().eulaAccepted(true).build())
				.extension(registration -> {
					for (ScenarioExtension extension : extensions) registration.scenarios(extension);
					registration.own(() -> events.add("shared"));
				}).build();
	}

	private AnvilScenario scenario(ScenarioHook hook) {
		return AnvilScenario.builder().name("test").entrypoint("server")
				.server(MinecraftServer.builder().name("server").platform("test")
						.distribution(Distribution.remote("1", "1")).build())
				.setupHook(hook).build();
	}

	private void await(CountDownLatch signal) {
		try {
			if (!signal.await(5, TimeUnit.SECONDS)) throw new AssertionError("Cleanup was not released");
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new AssertionError(failure);
		}
	}

	private class Context implements ScenarioContext {
		private final AnvilScenario scenario;
		private RuntimeException failure;
		private Runnable onStart = () -> { };

		private Context(AnvilScenario scenario) {
			this.scenario = scenario;
		}

		@Override
		public void start() { onStart.run(); }
		@Override
		public @NotNull AnvilScenario definition() { return scenario; }
		@Override
		public @NotNull ScenarioProcesses processes() { throw new UnsupportedOperationException(); }
		@Override
		public @NotNull PlayerManager players() { throw new UnsupportedOperationException(); }
		@Override
		public void finish(boolean successful) {
			events.add("context:" + successful);
			if (failure != null) throw failure;
		}
	}
}
