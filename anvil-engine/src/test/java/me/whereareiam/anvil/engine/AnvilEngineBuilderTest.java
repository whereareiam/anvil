package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.engine.EngineRegistration;
import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AnvilEngineBuilderTest {
	private final ScenarioExecutor unused = scenario -> { throw new AssertionError("No scenario starts during assembly"); };

	@Test
	void defersInstallationAndSealsTypedRegistrationsAfterOwnershipTransfer() {
		List<String> calls = new ArrayList<>();
		AtomicReference<EngineRegistration> installed = new AtomicReference<>();
		var builder = new AnvilEngineBuilder();
		builder.extension(registration -> {
			calls.add("installed");
			installed.set(registration);
			registration.executor(unused);
			registration.own(() -> calls.add("first"));
			registration.own(() -> calls.add("second"));
		});
		assertTrue(calls.isEmpty());

		var engine = builder.build();
		assertEquals(List.of("installed"), calls);
		assertThrows(IllegalStateException.class, () -> installed.get().executor(unused));
		assertThrows(IllegalStateException.class, () -> installed.get().scenarios(scenario -> successful -> { }));
		assertThrows(IllegalStateException.class, () -> installed.get().own(() -> { }));
		assertThrows(IllegalStateException.class, builder::build);
		assertThrows(IllegalStateException.class, () -> builder.extension(registration -> { }));
		assertThrows(IllegalStateException.class, () -> builder.options(EngineOptions.builder().build()));

		engine.close();
		engine.close();
		assertEquals(List.of("installed", "second", "first"), calls);
	}

	@Test
	void rollsBackPartialInstallationInReverseOwnershipOrderAndPreservesFailures() {
		List<String> closed = new ArrayList<>();
		AssertionError installation = new AssertionError("installation");
		IllegalStateException cleanup = new IllegalStateException("cleanup");
		var builder = new AnvilEngineBuilder().extension(registration -> {
			registration.own(() -> closed.add("first"));
			registration.own(() -> { closed.add("second"); throw cleanup; });
			throw installation;
		});

		assertSame(installation, assertThrows(AssertionError.class, builder::build));
		assertEquals(List.of("second", "first"), closed);
		assertSame(cleanup, installation.getSuppressed()[0]);
	}

	@Test
	void rollbackDoesNotSelfSuppressTheInstallationFailure() {
		IllegalStateException failure = new IllegalStateException("shared failure");
		var builder = new AnvilEngineBuilder().extension(registration -> {
			registration.own(() -> { throw failure; });
			throw failure;
		});

		assertSame(failure, assertThrows(IllegalStateException.class, builder::build));
		assertEquals(0, failure.getSuppressed().length);
	}

	@Test
	void missingOrDuplicateExecutorsReleaseTransferredResources() {
		List<String> closed = new ArrayList<>();
		var missing = new AnvilEngineBuilder().extension(registration -> registration.own(() -> closed.add("missing")));
		assertThrows(IllegalStateException.class, missing::build);
		var duplicate = new AnvilEngineBuilder().extension(registration -> {
			registration.own(() -> closed.add("duplicate"));
			registration.executor(unused);
			registration.executor(unused);
		});
		assertThrows(IllegalStateException.class, duplicate::build);
		assertEquals(List.of("missing", "duplicate"), closed);
	}

	@Test
	void rejectsDuplicateResourceOwnershipAndClosesItOnlyOnce() {
		List<String> closed = new ArrayList<>();
		AutoCloseable resource = () -> closed.add("resource");
		var builder = new AnvilEngineBuilder().extension(registration -> {
			registration.own(resource);
			registration.own(resource);
		});

		assertThrows(IllegalArgumentException.class, builder::build);
		assertEquals(List.of("resource"), closed);
	}

	@Test
	void preservesInterruptionWhileAttemptingRemainingSharedCleanup() {
		List<String> closed = new ArrayList<>();
		InterruptedException interrupted = new InterruptedException("cleanup interrupted");
		var engine = new AnvilEngineBuilder().extension(registration -> {
			registration.executor(unused);
			registration.own(() -> closed.add("remaining"));
			registration.own(() -> { throw interrupted; });
		}).build();

		try {
			AnvilException failure = assertThrows(AnvilException.class, engine::close);
			assertSame(interrupted, failure.getCause());
			assertTrue(Thread.currentThread().isInterrupted());
			assertEquals(List.of("remaining"), closed);
		} finally {
			Thread.interrupted();
		}
	}
}
