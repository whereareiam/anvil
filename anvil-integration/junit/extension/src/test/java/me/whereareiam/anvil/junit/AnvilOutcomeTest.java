package me.whereareiam.anvil.junit;

import me.whereareiam.anvil.api.runtime.AnvilContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class AnvilOutcomeTest {
	private static final List<String> outcomes = new ArrayList<>();
	private static boolean failCleanup;

	@Test
	void recordsSuccessAndFailuresFromEveryTestPhase() {
		for (Class<?> fixture : List.of(SuccessFixture.class, BodyFailureFixture.class,
				SetupFailureFixture.class, TeardownFailureFixture.class)) {
			outcomes.clear();
			failCleanup = false;
			var summary = execute(fixture);
			boolean success = fixture == SuccessFixture.class;
			assertEquals(success ? 0 : 1, summary.getSummary().getTestsFailedCount());
			assertEquals(List.of("context:" + success, "engine"), outcomes);
		}
	}

	@Test
	void preservesTheAssertionAndStillClosesTheEngineWhenCleanupFails() {
		outcomes.clear();
		failCleanup = true;
		try {
			var failure = execute(BodyFailureFixture.class).getSummary().getFailures().getFirst().getException();
			assertInstanceOf(AssertionError.class, failure);
			assertEquals("cleanup", failure.getSuppressed()[0].getMessage());
			assertEquals(List.of("context:false", "engine"), outcomes);
		} finally {
			failCleanup = false;
		}
	}

	private SummaryGeneratingListener execute(Class<?> fixture) {
		var listener = new SummaryGeneratingListener();
		LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
				.selectors(selectClass(fixture)).build(), listener);
		return listener;
	}

	static final class InstallScope implements BeforeEachCallback {
		@Override
		public void beforeEach(ExtensionContext owner) {
			AnvilContext context = (AnvilContext) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{AnvilContext.class}, (proxy, method, arguments) -> {
						if (!method.getName().equals("close") || arguments == null)
							throw new AssertionError("Expected outcome-aware close");
						outcomes.add("context:" + arguments[0]);
						if (failCleanup) throw new IllegalStateException("cleanup");
						return null;
					});
			owner.getStore(ExtensionContext.Namespace.create(AnvilOutcomeTest.class))
					.put("scope", new AnvilExtension.State(() -> outcomes.add("engine"), context, owner));
		}
	}

	@ExtendWith(InstallScope.class)
	static class SuccessFixture {
		@Test void passes() { }
	}

	@ExtendWith(InstallScope.class)
	static class BodyFailureFixture {
		@Test void fails() { fail("assertion"); }
	}

	@ExtendWith(InstallScope.class)
	static class SetupFailureFixture {
		@BeforeEach void setup() { fail("setup"); }
		@Test void passes() { }
	}

	@ExtendWith(InstallScope.class)
	static class TeardownFailureFixture {
		@AfterEach void teardown() { fail("teardown"); }
		@Test void passes() { }
	}
}
