package me.whereareiam.anvil.workbench.local;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LocalWorkbenchTest {
	@TempDir
	Path temporary;

	@Test
	void reportsTheMachinesCapacityEveryHostAndUnavailableExecutionProviders() {
		try (var workbench = new LocalWorkbench(options(), Map.of("ready", provider("ready", null),
				"stopped", provider("stopped", "the daemon does not answer")))) {
			WorkbenchFacts facts = workbench.facts();

			assertEquals("local", facts.getWorkbenchName());
			assertTrue(facts.getProcessors() >= 1);
			assertTrue(facts.getMemoryMegabytes() > 0);
			assertEquals(Set.of("ready", "stopped"), facts.getExecutions());
			assertEquals(Map.of("stopped", "the daemon does not answer"), facts.getUnavailableExecutions());
			assertEquals(List.of("*"), facts.getReachableHosts());
		}
	}

	@Test
	void opensSessionsWithItsOwnServicesAndTheScenariosNetworkPolicy() {
		NetworkPolicy policy = NetworkPolicy.builder().bindAddress("127.0.0.2").build();

		try (var workbench = new LocalWorkbench(options(), Map.of())) {
			ExecutionContext context = workbench.context(policy);

			assertSame(policy, context.getNetworkPolicy());
			assertEquals(temporary.resolve("cache"), context.getCacheDirectory());
			assertSame(context.getPorts(), workbench.context(policy).getPorts(), "One workbench reserves ports once");
		}
	}

	/**
	 * Options as the engine resolves them, which leaves no scheduling member open.
	 */
	private EngineOptions options() {
		return EngineOptions.builder()
				.cacheDirectory(temporary.resolve("cache"))
				.workDirectory(temporary.resolve("work"))
				.downloadParallelism(1)
				.processScheduling(ProcessScheduling.builder().priority(ProcessPriority.NORMAL).build())
				.build();
	}

	private static ExecutionProvider provider(String id, @Nullable String unavailable) {
		return new ExecutionProvider() {
			public @NotNull String id() { return id; }
			public boolean preservesClientAddress() { return true; }
			public boolean connectsEnvironments() { return true; }
			public @Nullable String unavailableReason() { return unavailable; }
			public @NotNull ExecutionSession open(@NotNull ExecutionContext context) { throw new AssertionError("No session is opened"); }
		};
	}
}
