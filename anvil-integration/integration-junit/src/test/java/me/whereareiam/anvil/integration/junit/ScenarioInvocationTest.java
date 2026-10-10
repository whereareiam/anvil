package me.whereareiam.anvil.integration.junit;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.opentest4j.TestAbortedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioInvocationTest {
	private final AnvilScenario definition = AnvilScenario.builder().name("invocation").entrypoint("server").build();

	@Test
	void leavesTheBorrowedEngineOpenWhenPreparationFails() {
		RuntimeException start = new IllegalStateException("start");
		ScenarioEngine engine = new ScenarioEngine() {
			public @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) { throw start; }
			public void close() { throw new AssertionError("The invocation borrows its engine"); }
		};

		assertSame(start, assertThrows(RuntimeException.class, () -> new ScenarioInvocation(engine, definition, () -> true)));
		assertEquals(0, start.getSuppressed().length);
	}

	@Test
	void transfersOpenResourcesToRegistrationUntilInvocationEnds() {
		List<String> actions = new ArrayList<>();
		var context = new Context(() -> actions.add("context"));
		var invocation = new ScenarioInvocation(engine(context), definition, () -> true);
		var registered = new AtomicReference<ScenarioInvocation>();

		invocation.register(registered::set);
		assertTrue(actions.isEmpty());
		assertEquals(1, context.starts);
		assertSame(context, registered.get().getContext());
		registered.get().close();
		assertEquals(List.of("context"), actions);
	}

	@Test
	void registrationFailureFinishesTheScenarioAndPreservesBothFailures() {
		RuntimeException registration = new IllegalStateException("registration");
		RuntimeException contextClose = new IllegalStateException("context close");
		var context = new Context(() -> { throw contextClose; });
		var invocation = new ScenarioInvocation(engine(context), definition, () -> true);

		assertSame(registration, assertThrows(RuntimeException.class,
				() -> invocation.register(ignored -> { throw registration; })));
		assertFalse(context.successful);
		assertArrayEquals(new Throwable[]{contextClose}, registration.getSuppressed());
	}

	@Test
	void readsTheFinalTestOutcomeWhenTheInvocationCloses() {
		List<String> actions = new ArrayList<>();
		AtomicBoolean successful = new AtomicBoolean(true);
		Context context = new Context(() -> actions.add("context"));
		var invocation = new ScenarioInvocation(engine(context), definition, successful::get);
		assertEquals(1, context.starts);
		successful.set(false);
		invocation.close();
		invocation.close();
		assertFalse(context.successful);
		assertEquals(List.of("context"), actions);
	}

	@Test
	void closesOwnedResourcesAfterTheScenario() {
		List<String> actions = new ArrayList<>();
		OwnedResources resources = new OwnedResources();
		resources.own((AutoCloseable) () -> actions.add("resource"));
		var invocation = new ScenarioInvocation(engine(new Context(() -> actions.add("context"))), definition, () -> true, null, resources);

		assertTrue(invocation.resource(AutoCloseable.class) != null);
		invocation.close();
		assertEquals(List.of("context", "resource"), actions);
	}

	@Test
	void closesOwnedResourcesWhenTheScenarioFailsToStart() {
		List<String> actions = new ArrayList<>();
		OwnedResources resources = new OwnedResources();
		resources.own((AutoCloseable) () -> actions.add("resource"));
		RuntimeException start = new IllegalStateException("start");
		ScenarioEngine engine = new ScenarioEngine() {
			public @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) { throw start; }
			public void close() { throw new AssertionError("The invocation borrows its engine"); }
		};

		assertSame(start, assertThrows(RuntimeException.class,
				() -> new ScenarioInvocation(engine, definition, () -> true, null, resources)));
		assertEquals(List.of("resource"), actions);
	}

	@Test
	void finishesThePreparedScenarioWithoutStartingItWhenTheMachineStoresTooFewAccounts() throws Exception {
		List<String> actions = new ArrayList<>();
		Context context = new Context(() -> actions.add("context"));
		context.stored.add(new AuthenticationAccount("alice", "library", "Alice", null));

		assertThrows(TestAbortedException.class, () -> new ScenarioInvocation(
				engine(context), definition, () -> true, requirement("twoAccounts")));
		assertEquals(0, context.starts);
		assertEquals(List.of("context"), actions);
		assertTrue(context.successful, "A scenario that was skipped did not fail");
	}

	@Test
	void poolsEveryUnambiguousStoredAccountAndReleasesThePoolWithTheInvocation() throws Exception {
		Context context = new Context(() -> { });
		context.stored.add(new AuthenticationAccount("alice", "library", "Alice", null));
		context.stored.add(new AuthenticationAccount("bob", "library", "Bob", null));
		context.stored.add(new AuthenticationAccount("shared", "library", null, null));
		context.stored.add(new AuthenticationAccount("shared", "other-library", null, null));

		var invocation = new ScenarioInvocation(engine(context), definition, () -> true, requirement("twoAccounts"));

		assertEquals(1, context.starts);
		assertEquals(List.of("alice", "bob"), context.pooled);
		assertEquals(2, invocation.getAccounts().accounts().size());
		invocation.close();
		assertTrue(context.poolClosed);
	}

	@Test
	void skipsWhenTheNamedPoolIsNotDeclared() throws Exception {
		Context context = new Context(() -> { });

		TestAbortedException skipped = assertThrows(TestAbortedException.class, () -> new ScenarioInvocation(
				engine(context), definition, () -> true, requirement("namedPool")));
		assertTrue(skipped.getMessage().contains("testers"), skipped.getMessage());
		assertEquals(0, context.starts);
	}

	@Test
	void rejectsARequirementBelowOneAccount() {
		assertThrows(ExtensionConfigurationException.class, () -> requirement("noAccounts"));
	}

	private static AccountRequirement requirement(String method) throws NoSuchMethodException {
		return AccountRequirement.of(Declarations.class.getDeclaredMethod(method), Declarations.class);
	}

	private static final class Declarations {
		@AnvilAccounts(2)
		void twoAccounts() {
		}

		@AnvilAccounts(pool = "testers")
		void namedPool() {
		}

		@AnvilAccounts(0)
		void noAccounts() {
		}
	}

	/**
	 * An engine that hands out one context and must stay open, as the invocation only borrows it.
	 */
	private ScenarioEngine engine(ScenarioContext context) {
		return new ScenarioEngine() {
			public @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) { return context; }
			public void close() { throw new AssertionError("The invocation borrows its engine"); }
		};
	}

	@RequiredArgsConstructor
	private final class Context implements ScenarioContext {
		private final Runnable cleanup;
		private int starts;
		private Boolean successful;
		private final List<AuthenticationAccount> stored = new ArrayList<>();
		private final List<String> pooled = new ArrayList<>();
		private boolean poolClosed;

		public void start() { starts++; }

		public @NonNull AnvilScenario definition() { return definition; }
		public @NonNull ScenarioProcesses processes() { throw new AssertionError("No process access during cleanup"); }
		public @NonNull PlayerManager players() { throw new AssertionError("No player access during cleanup"); }
		public void finish(boolean successful) { this.successful = successful; cleanup.run(); }

		public @NonNull AccountManager accounts() {
			return new AccountManager() {
				public @NonNull Collection<AuthenticationAccount> list() { return stored; }

				public @NonNull AccountPool pool(@NonNull Collection<String> accountIds) {
					pooled.addAll(accountIds);
					return new AccountPool() {
						public @NonNull AccountLease lease() { throw new AssertionError("No lease expected"); }
						public @NonNull Collection<AuthenticationAccount> accounts() {
							return stored.stream().filter(account -> pooled.contains(account.getAccountId())).toList();
						}
						public void close() { poolClosed = true; }
					};
				}

				public @NonNull AccountPool pool(@NonNull String poolName) {
					throw new IllegalArgumentException("Account pool '" + poolName + "' is not declared");
				}
			};
		}
	}
}
