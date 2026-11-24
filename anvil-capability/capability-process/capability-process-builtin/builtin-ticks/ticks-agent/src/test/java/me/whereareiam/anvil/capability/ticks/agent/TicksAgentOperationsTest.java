package me.whereareiam.anvil.capability.ticks.agent;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickCount;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicksAgentOperationsTest {
	private final RecordingPlatform platform = new RecordingPlatform();
	private final RecordingRegistry registry = new RecordingRegistry();
	private final RecordingTicks ticks = new RecordingTicks();
	private TickClock clock;

	@Test
	void ownsItsNamespaceAndRegistersEveryOperationOfTheChannel() {
		install();

		assertEquals("me.whereareiam.anvil.ticks", new Operations().id());
		assertEquals(List.of("me.whereareiam.anvil.ticks.count", "me.whereareiam.anvil.ticks.next",
				"me.whereareiam.anvil.ticks.sprint", "me.whereareiam.anvil.ticks.stop"), List.copyOf(registry.handlers.keySet()));
	}

	@Test
	void countsAndWaitsForTicksOnTheRequestsThread() throws Exception {
		install();
		clock.ticked();

		AgentTickCount count = registry.execute(TicksOperations.COUNT, null);
		assertEquals(1, count.getTick());
		clock.ticked();
		AgentTickWait wait = AgentTickWait.builder().run(count.getRun()).after(1).waitMillis(1_000).build();
		assertEquals(2, registry.execute(TicksOperations.NEXT, wait).getTick());
		assertEquals(0, platform.calls);
	}

	@Test
	void startsAndStopsSprintsOnThePlatformsThread() throws Exception {
		install();
		clock.ticked();

		assertEquals(1, registry.execute(TicksOperations.SPRINT, 600).getTick());
		assertEquals(List.of(600), ticks.sprints);
		assertTrue(registry.execute(TicksOperations.STOP, null));
		assertFalse(registry.execute(TicksOperations.STOP, null));
		assertEquals(3, platform.calls);
	}

	private void install() {
		new Operations().install(platform, registry);
	}

	private final class Operations extends TicksAgentOperations {
		@Override
		protected @NotNull ServerTicks open(@NotNull PlatformAgent platform, @NotNull TickClock clock) {
			TicksAgentOperationsTest.this.clock = clock;
			return ticks;
		}
	}

	private static final class RecordingTicks implements ServerTicks {
		private final List<Integer> sprints = new ArrayList<>();
		private boolean sprinting;

		@Override
		public void sprint(int ticks) {
			sprints.add(ticks);
			sprinting = true;
		}

		@Override
		public boolean stopSprinting() {
			boolean stopped = sprinting;
			sprinting = false;

			return stopped;
		}
	}

	private static final class RecordingPlatform implements PlatformAgent {
		private int calls;

		@Override
		public @NotNull AgentInfo info() {
			return AgentInfo.builder().platform("NeoForge").version("1.21.1").role(AgentRole.SERVER).build();
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			throw new AssertionError("The ticks use no commands");
		}

		@Override
		public <T> @Nullable T call(@NotNull Supplier<T> action) {
			calls++;
			return action.get();
		}
	}

	private final class RecordingRegistry implements AgentOperationRegistry {
		private final Map<String, AgentOperationHandler<?, ?>> handlers = new LinkedHashMap<>();

		@Override
		public <Q, R> void register(@NotNull AgentOperation<Q, R> operation, @NotNull AgentOperationHandler<Q, R> handler) {
			handlers.put(operation.getName(), handler);
		}

		@SuppressWarnings("unchecked")
		private <Q, R> R execute(AgentOperation<Q, R> operation, Q request) throws Exception {
			return ((AgentOperationHandler<Q, R>) handlers.get(operation.getName())).execute(platform, request);
		}
	}
}
