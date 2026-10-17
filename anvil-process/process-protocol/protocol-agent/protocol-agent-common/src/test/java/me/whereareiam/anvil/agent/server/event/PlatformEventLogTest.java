package me.whereareiam.anvil.agent.server.event;

import me.whereareiam.anvil.agent.api.model.AgentPlayerEvent;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import me.whereareiam.anvil.agent.api.type.AgentPlayerEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformEventLogTest {
	private static final AgentProfile ALICE = AgentProfile.builder().username("Alice").uniqueId(UUID.randomUUID()).build();

	@Test
	void answersTheEventsAfterAPositionInTheOrderTheyHappened() {
		PlatformEventLog log = new PlatformEventLog(true);
		log.joined(ALICE, "auth");
		log.left(ALICE, "auth");
		log.joined(ALICE, "lobby");

		AgentPlayerEventsResponse later = log.after(1);

		assertEquals(List.of(2L, 3L), later.getEvents().stream().map(AgentPlayerEvent::getPosition).toList());
		assertEquals(List.of(AgentPlayerEventType.LEFT, AgentPlayerEventType.JOINED), later.getEvents().stream().map(AgentPlayerEvent::getType).toList());
		assertEquals(List.of("auth", "lobby"), later.getEvents().stream().map(AgentPlayerEvent::getServer).toList());
		assertFalse(later.isTruncated());
		assertEquals(later.getGeneration(), log.after(0).getGeneration());
		assertNotEquals(later.getGeneration(), new PlatformEventLog(true).after(0).getGeneration());
	}

	@Test
	void dropsTheOldestEventsBeyondItsLimitAndSaysSo() {
		PlatformEventLog log = new PlatformEventLog(true);
		for (int index = 0; index < PlatformEventLog.LIMIT + 2; index++) log.joined(ALICE, null);

		AgentPlayerEventsResponse response = log.after(0);

		assertEquals(PlatformEventLog.LIMIT, response.getEvents().size());
		assertEquals(3, response.getEvents().getFirst().getPosition());
		assertTrue(response.isTruncated());
	}
}
