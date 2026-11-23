package me.whereareiam.anvil.capability.vitals;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.vitals.channel.VitalsChannel;
import me.whereareiam.anvil.capability.vitals.model.Death;
import me.whereareiam.anvil.capability.vitals.model.VitalStatus;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelVitalsTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final Death FELL = new Death("death.fell.accident.generic Alice", false);
	private static final Death KILLED = new Death("death.attack.genericKill Alice", false);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(VitalsChannel.ID));
	private final ChannelVitals vitals = new ChannelVitals(channel);

	@Test
	void startsWithTheValuesAClientStartsWithAndFollowsTheLatestStatus() {
		assertEquals(20F, vitals.health());
		assertEquals(20, vitals.food());
		assertEquals(5F, vitals.saturation());
		assertFalse(vitals.dead());

		channel.publish(VitalsChannel.STATUS, new VitalStatus(7.5F, 12, 0F));

		assertEquals(7.5F, vitals.health());
		assertEquals(12, vitals.food());
		assertEquals(0F, vitals.saturation());
	}

	@Test
	void isDeadWhileItsHealthIsZeroOrLess() {
		channel.publish(VitalsChannel.STATUS, new VitalStatus(0F, 20, 5F));
		assertTrue(vitals.dead());

		channel.publish(VitalsChannel.STATUS, new VitalStatus(-2F, 20, 5F));
		assertTrue(vitals.dead());

		channel.publish(VitalsChannel.STATUS, VitalStatus.INITIAL);
		assertFalse(vitals.dead());
	}

	@Test
	void isDeadFromTheReportedDeathUntilTheServerReportsItAlive() {
		channel.publish(VitalsChannel.DIED, KILLED);
		assertTrue(vitals.dead());

		channel.publish(VitalsChannel.STATUS, new VitalStatus(0F, 20, 5F));
		assertTrue(vitals.dead());

		channel.publish(VitalsChannel.STATUS, VitalStatus.INITIAL);
		assertFalse(vitals.dead());
	}

	@Test
	void staysAliveAfterADeathItRespawnedFromByItself() {
		channel.publish(VitalsChannel.DIED, new Death("death.attack.genericKill Alice", true));

		assertFalse(vitals.dead());
		assertEquals(1, vitals.deaths().size());
	}

	@Test
	void keepsEveryDeathInOrder() {
		channel.publish(VitalsChannel.DIED, FELL);
		channel.publish(VitalsChannel.STATUS, VitalStatus.INITIAL);
		channel.publish(VitalsChannel.DIED, KILLED);

		assertEquals(List.of(FELL, KILLED), vitals.deaths());
		assertThrows(UnsupportedOperationException.class, () -> vitals.deaths().clear());
	}

	@Test
	void watchesOnlyDeathsSinceItAndContinuesAfterEachMatch() {
		channel.publish(VitalsChannel.DIED, FELL);
		DeathWatch deaths = vitals.watchDeaths();

		assertEquals("Player did not die", assertThrows(IllegalStateException.class,
				() -> deaths.died(TIMEOUT)).getMessage());

		channel.publish(VitalsChannel.DIED, FELL);
		channel.publish(VitalsChannel.DIED, KILLED);
		assertEquals(KILLED, deaths.died(death -> death.message().contains("genericKill"), TIMEOUT));
		assertEquals(List.of(), deaths.unread());
		assertThrows(IllegalStateException.class, () -> deaths.died(TIMEOUT));
	}

	@Test
	void failsWhenTheForbiddenDeathHappenedAndPassesOtherwise() {
		DeathWatch deaths = vitals.watchDeaths();
		deaths.neverDied(TIMEOUT);

		channel.publish(VitalsChannel.DIED, FELL);
		deaths.neverDied(death -> death.message().contains("genericKill"), TIMEOUT);
		assertEquals("Player died when it must not: death.fell.accident.generic Alice",
				assertThrows(IllegalStateException.class, () -> deaths.neverDied(TIMEOUT)).getMessage());
		assertEquals(List.of(FELL), deaths.unread());
	}

	@Test
	void expectsADeathForWaitingOnSeveralThings() {
		Expectation death = vitals.watchDeaths().expectDied(any -> true);

		assertFalse(death.met());
		channel.publish(VitalsChannel.DIED, KILLED);
		assertTrue(death.met());
	}

	@Test
	void respawnsTheDeadPlayerAndWaitsUntilTheServerReportsItAlive() {
		channel.publish(VitalsChannel.DIED, KILLED);
		channel.answer(VitalsChannel.RESPAWN, ignored -> null);
		channel.meanwhile(() -> channel.publish(VitalsChannel.STATUS, new VitalStatus(20F, 20, 5F)));

		vitals.respawn(TIMEOUT);

		assertEquals(List.of(VitalsChannel.RESPAWN.id()), channel.requests());
		assertFalse(vitals.dead());
	}

	@Test
	void failsWhenTheServerDoesNotRespawnThePlayerInTime() {
		channel.publish(VitalsChannel.STATUS, new VitalStatus(0F, 20, 5F));
		channel.answer(VitalsChannel.RESPAWN, ignored -> null);
		channel.meanwhile(() -> channel.publish(VitalsChannel.STATUS, new VitalStatus(0F, 20, 5F)));

		assertEquals("Player did not respawn", assertThrows(IllegalStateException.class,
				() -> vitals.respawn(TIMEOUT)).getMessage());
	}

	@Test
	void refusesToRespawnAPlayerThatIsAlive() {
		assertEquals("Player cannot respawn, it is not dead (health 20.0)",
				assertThrows(IllegalStateException.class, () -> vitals.respawn(TIMEOUT)).getMessage());
		assertTrue(channel.requests().isEmpty());
	}
}
