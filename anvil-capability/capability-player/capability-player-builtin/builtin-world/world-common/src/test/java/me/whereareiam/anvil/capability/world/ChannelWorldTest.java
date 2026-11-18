package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.environment.SpawnPoint;
import me.whereareiam.anvil.capability.world.model.environment.Weather;
import me.whereareiam.anvil.capability.world.model.environment.WorldBorder;
import me.whereareiam.anvil.capability.world.model.environment.WorldState;
import me.whereareiam.anvil.capability.world.model.environment.WorldTime;
import me.whereareiam.anvil.capability.world.model.level.DimensionChange;
import me.whereareiam.anvil.capability.world.model.level.DimensionType;
import me.whereareiam.anvil.capability.world.model.level.Level;
import me.whereareiam.anvil.capability.world.model.level.ServerLevels;
import me.whereareiam.anvil.capability.world.type.Environment;
import me.whereareiam.anvil.capability.world.type.LevelGeneration;
import me.whereareiam.anvil.capability.world.type.WeatherType;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelWorldTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(WorldChannel.ID));
	private final ChannelWorld world = new ChannelWorld(channel);

	@Test
	void keepsEveryChangeInOrderAndTheDimensionOfTheLast() {
		assertEquals(Optional.empty(), world.dimension());

		channel.publish(WorldChannel.DIMENSION, change(null, OVERWORLD));
		channel.publish(WorldChannel.DIMENSION, change(OVERWORLD, NETHER));

		assertEquals(Optional.of(NETHER), world.dimension());
		assertEquals(List.of(change(null, OVERWORLD), change(OVERWORLD, NETHER)), world.changes());
		assertThrows(UnsupportedOperationException.class, () -> world.changes().clear());
	}

	@Test
	void keepsTheLevelOfTheLastChangeAndTheServerOfTheLastJoin() {
		ServerLevels server = ServerLevels.builder().hardcore(true).level(OVERWORLD).level(NETHER).build();
		Level nether = new Level(NETHER, new DimensionType(NETHER, Environment.NETHER, 0, 256),
				LevelGeneration.FLAT);
		channel.publish(WorldChannel.SERVER, server);
		channel.publish(WorldChannel.DIMENSION, change(null, OVERWORLD));
		channel.publish(WorldChannel.DIMENSION, new DimensionChange(OVERWORLD, NETHER, nether));

		assertEquals(Optional.of(nether), world.level());
		assertEquals(Optional.of(server), world.server());
	}

	@Test
	void isInNoDimensionOnceTheConnectionLeftItAndKeepsTheHistory() {
		channel.publish(WorldChannel.SERVER, ServerLevels.builder().level(NETHER).build());
		channel.publish(WorldChannel.DIMENSION, change(null, NETHER));
		channel.publish(WorldChannel.LEFT, NETHER);

		assertEquals(Optional.empty(), world.dimension());
		assertEquals(Optional.empty(), world.level());
		assertEquals(Optional.empty(), world.server());
		assertEquals(List.of(change(null, NETHER)), world.changes());
	}

	@Test
	void entersADimensionByJoiningOrComingFromAnotherButNotByRespawningInIt() {
		DimensionWatch dimensions = world.dimensions();

		channel.publish(WorldChannel.DIMENSION, change(null, OVERWORLD));
		assertEquals(change(null, OVERWORLD), dimensions.entered(OVERWORLD, TIMEOUT));

		channel.publish(WorldChannel.DIMENSION, change(OVERWORLD, OVERWORLD));
		assertEquals("Player did not enter a matching dimension", assertThrows(IllegalStateException.class,
				() -> dimensions.entered(OVERWORLD, TIMEOUT)).getMessage());

		channel.publish(WorldChannel.DIMENSION, change(OVERWORLD, NETHER));
		assertEquals(change(OVERWORLD, NETHER), dimensions.entered("the_nether", TIMEOUT));
		assertEquals(List.of(), dimensions.unread());
	}

	@Test
	void watchesOnlyChangesSinceItAndContinuesAfterEachMatch() {
		channel.publish(WorldChannel.DIMENSION, change(null, NETHER));
		DimensionWatch dimensions = world.dimensions();

		assertThrows(IllegalStateException.class, () -> dimensions.entered(NETHER, TIMEOUT));

		channel.publish(WorldChannel.DIMENSION, change(NETHER, OVERWORLD));
		channel.publish(WorldChannel.DIMENSION, change(OVERWORLD, NETHER));
		assertEquals(change(OVERWORLD, NETHER),
				dimensions.changed(change -> NETHER.equals(change.getTo()), TIMEOUT));
		assertThrows(IllegalStateException.class, () -> dimensions.changed(change -> true, TIMEOUT));
	}

	@Test
	void failsWhenTheForbiddenDimensionWasEnteredAndPassesOtherwise() {
		DimensionWatch dimensions = world.dimensions();
		channel.publish(WorldChannel.DIMENSION, change(null, OVERWORLD));

		dimensions.neverEntered(NETHER, TIMEOUT);
		assertEquals("Player made a dimension change it must not make: joined minecraft:overworld",
				assertThrows(IllegalStateException.class, () -> dimensions.neverEntered(OVERWORLD, TIMEOUT)).getMessage());

		channel.publish(WorldChannel.DIMENSION, change(OVERWORLD, NETHER));
		assertEquals("Player made a dimension change it must not make: from minecraft:overworld to minecraft:the_nether",
				assertThrows(IllegalStateException.class, () -> dimensions.neverEntered(NETHER, TIMEOUT)).getMessage());
		assertEquals(2, dimensions.unread().size());
	}

	@Test
	void expectsAChangeForWaitingOnSeveralThings() {
		DimensionWatch dimensions = world.dimensions();
		Expectation nether = dimensions.expectChanged(DimensionWatch.entering(NETHER));

		assertFalse(nether.met());
		channel.publish(WorldChannel.DIMENSION, change(null, NETHER));
		assertTrue(nether.met());
	}

	@Test
	void tellsTheLastWorldStateAndNothingOnceTheConnectionEnded() {
		assertEquals(WorldState.EMPTY, world.states().current());
		assertEquals(Optional.empty(), world.time());

		WorldState state = WorldState.builder()
				.time(WorldTime.builder().gameTime(10).dayTime(6000L).advancing(true).build())
				.weather(Weather.CLEAR)
				.border(WorldBorder.builder().size(100).lerpFrom(100).build())
				.spawn(SpawnPoint.builder().dimension(OVERWORLD).y(64).build())
				.build();
		channel.publish(WorldChannel.STATE, state);

		assertEquals(Optional.of(state.getTime()), world.time());
		assertEquals(Optional.of(Weather.CLEAR), world.weather());
		assertEquals(Optional.of(state.getBorder()), world.border());
		assertEquals(Optional.of(state.getSpawn()), world.spawn());

		channel.publish(WorldChannel.STATE, WorldState.EMPTY);
		assertEquals(Optional.empty(), world.spawn());
	}

	@Test
	void waitsForAStateThatHoldsNowOrOnlyBrieflyMeanwhile() {
		WorldStateWatch states = world.states();
		channel.publish(WorldChannel.STATE, raining(false));
		assertEquals(raining(false), states.until(state -> state.getWeather() != null, TIMEOUT));

		channel.meanwhile(() -> {
			channel.publish(WorldChannel.STATE, raining(true));
			channel.publish(WorldChannel.STATE, raining(false));
		});
		assertEquals(raining(true), states.until(ChannelWorldTest::rains, TIMEOUT));
		assertEquals("Player did not reach a matching world state", assertThrows(IllegalStateException.class,
				() -> states.until(ChannelWorldTest::rains, TIMEOUT)).getMessage());
	}

	@Test
	void failsWhenAForbiddenStateHoldsEvenBrieflyAndPassesOtherwise() {
		WorldStateWatch states = world.states();
		channel.publish(WorldChannel.STATE, raining(false));
		states.never(ChannelWorldTest::rains, TIMEOUT);

		channel.meanwhile(() -> {
			channel.publish(WorldChannel.STATE, raining(true));
			channel.publish(WorldChannel.STATE, raining(false));
		});
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> states.never(ChannelWorldTest::rains, TIMEOUT));
		assertTrue(failure.getMessage().startsWith("Player's world reached a state it must not reach: "));
	}

	@Test
	void expectsAStateForWaitingOnSeveralThings() {
		Expectation rain = world.states().expect(state -> state.weatherType() == WeatherType.RAIN);

		assertFalse(rain.met());
		channel.publish(WorldChannel.STATE, raining(true));
		assertTrue(rain.met());
	}

	private static boolean rains(WorldState state) {
		return state.weatherType() == WeatherType.RAIN;
	}

	private static WorldState raining(boolean raining) {
		return WorldState.builder()
				.weather(Weather.builder().rainLevel(raining ? 1 : 0).build())
				.build();
	}

	private static DimensionChange change(String from, String to) {
		return new DimensionChange(from, to, level(to));
	}

	private static Level level(String dimension) {
		return new Level(dimension, new DimensionType(dimension, Environment.OVERWORLD, -64, 384),
				LevelGeneration.NORMAL);
	}
}
