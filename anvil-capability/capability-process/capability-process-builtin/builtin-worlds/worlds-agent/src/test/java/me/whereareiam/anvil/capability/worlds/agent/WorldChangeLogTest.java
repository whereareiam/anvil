package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChange;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChangesRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorldChangeLogTest {
	private static final UUID ALICE = new UUID(0, 1);

	private final AtomicLong clock = new AtomicLong(1_000);
	private final WorldChangeLog log = new WorldChangeLog("log-1", clock::get);

	@Test
	void numbersEachChangeAndTimesIt() {
		log.record(ALICE, "Alice", "minecraft:overworld", "minecraft:the_nether");
		clock.set(2_000);
		log.record(ALICE, "Alice", "minecraft:the_nether", "minecraft:arena");

		assertEquals(AgentWorldChanges.builder()
				.log("log-1")
				.change(change(1, "minecraft:overworld", "minecraft:the_nether", 1_000))
				.change(change(2, "minecraft:the_nether", "minecraft:arena", 2_000))
				.build(), log.since(request(null, 0)));
	}

	@Test
	void returnsOnlyTheChangesAfterThoseTheHostRead() {
		log.record(ALICE, "Alice", "minecraft:overworld", "minecraft:the_nether");
		log.record(ALICE, "Alice", "minecraft:the_nether", "minecraft:overworld");

		assertEquals(List.of(change(2, "minecraft:the_nether", "minecraft:overworld", 1_000)),
				log.since(request("log-1", 1)).getChanges());
		assertEquals(List.of(), log.since(request("log-1", 2)).getChanges());
		assertEquals(List.of(), log.since(request("log-1", 5)).getChanges());
	}

	@Test
	void returnsEveryChangeToAHostThatReadAnotherLog() {
		log.record(ALICE, "Alice", "minecraft:overworld", "minecraft:the_nether");

		assertEquals(1, log.since(request("log-of-the-previous-process", 7)).getChanges().size());
	}

	@Test
	void startsEachLogUnderANewIdentity() {
		assertNotEquals(new WorldChangeLog().since(request(null, 0)).getLog(),
				new WorldChangeLog().since(request(null, 0)).getLog());
	}

	private static AgentWorldChangesRequest request(String log, long after) {
		return AgentWorldChangesRequest.builder().log(log).after(after).build();
	}

	private static AgentWorldChange change(long position, String from, String to, long at) {
		return AgentWorldChange.builder()
				.position(position)
				.uniqueId(ALICE)
				.username("Alice")
				.from(from)
				.to(to)
				.at(at)
				.build();
	}
}
