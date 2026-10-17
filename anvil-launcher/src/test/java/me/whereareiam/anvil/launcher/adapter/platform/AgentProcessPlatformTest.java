package me.whereareiam.anvil.launcher.adapter.platform;

import me.whereareiam.anvil.agent.api.model.AgentPlayerEvent;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsRequest;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import me.whereareiam.anvil.agent.api.type.AgentPlayerEventType;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException;
import me.whereareiam.anvil.capability.api.model.platform.PlatformEvent;
import me.whereareiam.anvil.capability.api.type.PlatformEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProcessPlatformTest {
	private static final AgentProfile ALICE = AgentProfile.builder().username("Alice").uniqueId(UUID.randomUUID()).build();

	@Test
	void readsOnlyNewEventsAndNumbersThemItself() {
		Agent agent = new Agent("first");
		agent.record(AgentPlayerEventType.JOINED, "lobby");
		AgentProcessPlatform platform = new AgentProcessPlatform("proxy", () -> Optional.of(agent), Instant.EPOCH);

		assertEquals(List.of(1L), platform.events().stream().map(PlatformEvent::getPosition).toList());
		agent.record(AgentPlayerEventType.LEFT, "lobby");
		List<PlatformEvent> events = platform.events();

		assertEquals(List.of(PlatformEventType.JOINED, PlatformEventType.LEFT), events.stream().map(PlatformEvent::getType).toList());
		assertEquals(List.of(1L, 2L), events.stream().map(PlatformEvent::getPosition).toList());
		assertEquals("proxy", events.getFirst().getProcess());
		assertEquals("lobby", events.getFirst().getServer());
		assertEquals(List.of(0L, 1L), agent.asked);
	}

	@Test
	void endsTheStaysOfARestartedProcessBeforeItsNewGenerationAndKeepsNumbering() {
		long now = System.currentTimeMillis();
		Agent agent = new Agent("first");
		agent.record(AgentPlayerEventType.JOINED, "lobby", now - 2000);
		AgentProcessPlatform platform = new AgentProcessPlatform("proxy", () -> Optional.of(agent), Instant.EPOCH);
		platform.events();

		agent.restart("second");
		agent.record(AgentPlayerEventType.JOINED, "auth", now - 1000);
		List<PlatformEvent> events = platform.events();

		assertEquals(List.of(PlatformEventType.JOINED, PlatformEventType.LEFT, PlatformEventType.JOINED),
				events.stream().map(PlatformEvent::getType).toList());
		assertEquals(List.of("lobby", "lobby", "auth"), events.stream().map(PlatformEvent::getServer).toList());
		assertEquals(List.of(1L, 2L, 3L), events.stream().map(PlatformEvent::getPosition).toList());
		assertEquals(events, events.stream().sorted(Comparator.comparing(PlatformEvent::getAt)).toList());
	}

	@Test
	void leavesOutWhatHappenedBeforeTheScenarioBegan() {
		Agent agent = new Agent("first");
		agent.record(AgentPlayerEventType.JOINED, "lobby");

		assertEquals(List.of(), new AgentProcessPlatform("proxy", () -> Optional.of(agent), Instant.now().plusSeconds(60)).events());
	}

	@Test
	void runsCommandsAndReportsAnUnreachableAgent() {
		Agent agent = new Agent("first");
		AgentProcessPlatform platform = new AgentProcessPlatform("lobby", () -> Optional.of(agent), Instant.EPOCH);

		assertTrue(platform.execute("accepted"));
		assertFalse(platform.execute("rejected"));

		agent.available = false;
		assertThrows(ChannelUnavailableException.class, () -> platform.execute("accepted"));
		assertEquals(List.of(), platform.events());
		assertThrows(ChannelUnavailableException.class,
				() -> new AgentProcessPlatform("lobby", Optional::empty, Instant.EPOCH).execute("accepted"));
	}

	/**
	 * An agent connection with the events of one proxy generation a test adds to.
	 */
	private static final class Agent implements AgentConnection {
		private final List<AgentPlayerEvent> events = new ArrayList<>();
		private final List<Long> asked = new ArrayList<>();
		private String generation;
		private boolean available = true;

		private Agent(String generation) {
			this.generation = generation;
		}

		private void record(AgentPlayerEventType type, String server) {
			record(type, server, System.currentTimeMillis());
		}

		private void record(AgentPlayerEventType type, String server, long at) {
			events.add(AgentPlayerEvent.builder().position(events.size() + 1).at(at).type(type).profile(ALICE)
					.server(server).build());
		}

		private void restart(String replacement) {
			generation = replacement;
			events.clear();
		}

		@Override
		public boolean available() {
			return available;
		}

		@Override
		public <T> @Nullable T request(@NotNull String operation, @Nullable Object arguments, @NotNull Class<T> responseType) {
			if (!available) throw new AgentUnavailableException("The connection is closed");
			if (operation.equals(AgentOperations.COMMAND.getName()))
				return responseType.cast(AgentCommandResponse.builder()
						.accepted(((AgentCommandRequest) arguments).getCommand().equals("accepted"))
						.build());

			long after = ((AgentPlayerEventsRequest) arguments).getAfter();
			asked.add(after);
			return responseType.cast(AgentPlayerEventsResponse.builder()
					.generation(generation)
					.proxy(true)
					.events(events.stream().filter(event -> event.getPosition() > after).toList())
					.build());
		}

		@Override
		public void close() {
			available = false;
		}
	}
}
