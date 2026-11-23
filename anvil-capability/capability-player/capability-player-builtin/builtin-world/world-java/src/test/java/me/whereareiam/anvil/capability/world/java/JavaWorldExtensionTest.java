package me.whereareiam.anvil.capability.world.java;

import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.environment.Clock;
import me.whereareiam.anvil.capability.world.model.environment.SpawnPoint;
import me.whereareiam.anvil.capability.world.model.environment.Weather;
import me.whereareiam.anvil.capability.world.model.environment.WorldBorder;
import me.whereareiam.anvil.capability.world.model.environment.WorldState;
import me.whereareiam.anvil.capability.world.model.environment.WorldTime;
import me.whereareiam.anvil.capability.world.model.level.DimensionChange;
import me.whereareiam.anvil.capability.world.model.level.Level;
import me.whereareiam.anvil.capability.world.model.level.ServerLevels;
import me.whereareiam.anvil.capability.world.type.Environment;
import me.whereareiam.anvil.capability.world.type.LevelGeneration;
import me.whereareiam.anvil.capability.world.type.WeatherType;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.ClockState;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaWorldExtensionTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String ARENA = "minecraft:arena";
	private static final String END = "minecraft:the_end";

	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();
	private final JavaWorldExtension extension = new JavaWorldExtension();

	@Test
	void isDiscoveredAsTheEditionWideWorldExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaWorldExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.world", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void emitsTheLevelTheConnectionJoinsAndEachRespawnWithTheDimensionBefore() {
		connect();

		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		player.deliver(respawn(NETHER, vanilla(NETHER)));
		player.deliver(respawn(NETHER, vanilla(NETHER)));

		assertEquals(List.of(change(null, OVERWORLD, Environment.OVERWORLD), change(OVERWORLD, NETHER, Environment.NETHER),
				change(NETHER, NETHER, Environment.NETHER)), player.emitted(WorldChannel.DIMENSION));
		assertEquals(List.of("world.lookup", "world.chunk", "world.chunks", "world.watch", "world.unwatch"),
				operations.registered());
	}

	@Test
	void describesTheServerOnEachJoinAndTheLevelWithItsTypeAndGeneration() {
		connect();

		player.deliver(ClientboundLogin.builder()
				.entityId(new EntityId(7))
				.level(OVERWORLD)
				.level(ARENA)
				.hardcore(true)
				.dimension(ARENA)
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end", 0, 256))
				.flat(true)
				.build());
		player.deliver(ClientboundRespawn.builder()
				.dimension(ARENA)
				.dimensionType(DimensionType.builder().key("example:modded").minY(-32).height(128).build())
				.debug(true)
				.build());

		assertEquals(List.of(ServerLevels.builder().hardcore(true).level(OVERWORLD).level(ARENA).build()),
				player.emitted(WorldChannel.SERVER));
		assertEquals(List.of(
				new Level(ARENA, levelType("example:void", Environment.END, 0, 256), LevelGeneration.FLAT),
				new Level(ARENA, levelType("example:modded", Environment.CUSTOM, -32, 128), LevelGeneration.DEBUG)
		), player.emitted(WorldChannel.DIMENSION).stream().map(DimensionChange::level).toList());
	}

	@Test
	void keepsTheDimensionWhenAProxyJoinsTheConnectionToAnotherServersLevel() {
		connect();

		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		player.deliver(login(8, ARENA, vanilla(OVERWORLD)));

		assertEquals(Arrays.asList(null, OVERWORLD), player.emitted(WorldChannel.DIMENSION).stream()
				.map(DimensionChange::from).toList());
	}

	@Test
	void leavesTheDimensionWhenTheConnectionEndsAndStartsTheNextConnectionInNone() {
		connect();
		player.deliver(login(7, NETHER, vanilla(NETHER)));

		player.rejoin();
		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		player.disconnect();

		assertEquals(List.of(NETHER, OVERWORLD), player.emitted(WorldChannel.LEFT));
		assertEquals(List.of(change(null, NETHER, Environment.NETHER), change(null, OVERWORLD, Environment.OVERWORLD)),
				player.emitted(WorldChannel.DIMENSION));
	}

	@Test
	void leavesNothingForAConnectionThatEndsBeforeJoiningALevel() {
		connect();
		player.disconnect();

		assertTrue(player.emitted(WorldChannel.LEFT).isEmpty());
	}

	@Test
	void emitsNothingOnceTheBindingCloses() {
		WorkerBinding binding = extension.bind(player, operations);
		player.connect();
		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));

		binding.close();
		player.deliver(respawn(NETHER, vanilla(NETHER)));
		player.disconnect();

		assertEquals(List.of(change(null, OVERWORLD, Environment.OVERWORLD)), player.emitted(WorldChannel.DIMENSION));
		assertTrue(player.emitted(WorldChannel.LEFT).isEmpty());
		for (Class<? extends ClientboundPacket> type : List.of(
				ClientboundLogin.class,
				ClientboundRespawn.class,
				ClientboundSetTime.class,
				ClientboundGameEvent.class,
				ClientboundInitializeBorder.class,
				ClientboundSetBorderCenter.class,
				ClientboundSetBorderLerpSize.class,
				ClientboundSetBorderSize.class,
				ClientboundSetBorderWarningDelay.class,
				ClientboundSetBorderWarningDistance.class,
				ClientboundSetDefaultSpawnPosition.class
		))
			assertFalse(player.protocol().wants(type), type.getSimpleName());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		silent.connect();

		assertEquals(List.of(), silent.emittedEvents());
	}

	@Test
	void reportsTheTimeOfDayTheServerSendsBeforeWorldClocks() {
		connect();
		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));

		player.deliver(ClientboundSetTime.builder().gameTime(500).dayTime(30000L).tickDayTime(false).build());

		assertEquals(WorldTime.builder().gameTime(500).dayTime(30000L).advancing(false).build(), lastState().time());
		assertEquals(6000, lastState().time().timeOfDay().orElseThrow());
	}

	@Test
	void followsTheClockOfTheLevelsDimensionTypeFromWorldClocks() {
		connect();
		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD).toBuilder().defaultClock(OVERWORLD).build()));

		player.deliver(ClientboundSetTime.builder()
				.gameTime(500)
				.clockUpdate(OVERWORLD, new ClockState(13000, 0, 1))
				.clockUpdate(END, new ClockState(40, 0, 0))
				.build());
		WorldTime overworld = lastState().time();
		player.deliver(respawn(NETHER, vanilla(NETHER)));
		WorldTime nether = lastState().time();
		player.deliver(respawn(END, vanilla(END).toBuilder().defaultClock(END).build()));
		WorldTime end = lastState().time();

		assertEquals(13000L, overworld.dayTime());
		assertTrue(overworld.advancing());
		assertEquals(Map.of(OVERWORLD, new Clock(13000, 1), END, new Clock(40, 0)), overworld.clocks());
		assertNull(nether.dayTime());
		assertFalse(nether.advancing());
		assertEquals(40L, end.dayTime());
		assertFalse(end.advancing());
	}

	@Test
	void keepsTheWeatherOfTheLevelAndClearsItInAnotherOne() {
		connect();
		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		assertEquals(Weather.CLEAR, lastState().weather());

		player.deliver(new ClientboundGameEvent(GameEventType.START_RAINING, 0));
		player.deliver(new ClientboundGameEvent(GameEventType.RAIN_LEVEL_CHANGE, 1));
		player.deliver(new ClientboundGameEvent(GameEventType.THUNDER_LEVEL_CHANGE, 1));
		assertEquals(Weather.builder().rainLevel(1).thunderLevel(1).build(), lastState().weather());
		assertEquals(WeatherType.THUNDER, lastState().weatherType());

		player.deliver(respawn(OVERWORLD, vanilla(OVERWORLD)));
		assertEquals(WeatherType.THUNDER, lastState().weatherType());

		player.deliver(new ClientboundGameEvent(GameEventType.RAIN_LEVEL_CHANGE, 0.1F));
		player.deliver(new ClientboundGameEvent(GameEventType.STOP_RAINING, 0));
		assertEquals(Weather.builder().rainLevel(1).thunderLevel(1).build(), lastState().weather());
		player.deliver(new ClientboundGameEvent(GameEventType.START_RAINING, 0));
		assertEquals(WeatherType.CLEAR, lastState().weatherType());

		player.deliver(respawn(NETHER, vanilla(NETHER)));
		assertEquals(Weather.CLEAR, lastState().weather());
	}

	@Test
	void followsTheWorldBorderOfTheLevel() {
		connect();
		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		assertNull(lastState().border());

		player.deliver(ClientboundInitializeBorder.builder()
				.centerX(1)
				.centerZ(2)
				.oldSize(100)
				.newSize(100)
				.absoluteMaxSize(1000)
				.warningBlocks(5)
				.warningTime(15)
				.build());
		player.deliver(new ClientboundSetBorderLerpSize(100, 50, 200));
		WorldBorder moving = lastState().border();
		player.deliver(new ClientboundSetBorderCenter(10, 20));
		player.deliver(new ClientboundSetBorderSize(64));
		player.deliver(new ClientboundSetBorderWarningDelay(30));
		player.deliver(new ClientboundSetBorderWarningDistance(8));

		assertEquals(WorldBorder.builder().centerX(1).centerZ(2).size(50).lerpFrom(100).lerpTicks(200)
				.absoluteMaxSize(1000).warningBlocks(5).warningTime(15).build(), moving);
		assertEquals(WorldBorder.builder().centerX(10).centerZ(20).size(64).lerpFrom(64)
				.absoluteMaxSize(1000).warningBlocks(8).warningTime(30).build(), lastState().border());

		player.deliver(respawn(NETHER, vanilla(NETHER)));
		assertNull(lastState().border());
	}

	@Test
	void placesASpawnPointWithoutALevelInTheLevelThePlayerIsIn() {
		connect();
		player.deliver(login(7, NETHER, vanilla(NETHER)));

		player.deliver(ClientboundSetDefaultSpawnPosition.builder()
				.position(BlockPosition.builder().x(1).y(64).z(-3).build())
				.yaw(90)
				.build());
		SpawnPoint inferred = lastState().spawn();
		player.deliver(ClientboundSetDefaultSpawnPosition.builder()
				.dimension(OVERWORLD)
				.position(BlockPosition.builder().x(0).y(70).z(0).build())
				.yaw(180)
				.pitch(15)
				.build());

		assertEquals(SpawnPoint.builder().dimension(NETHER).x(1).y(64).z(-3).yaw(90).build(), inferred);
		assertEquals(SpawnPoint.builder().dimension(OVERWORLD).y(70).yaw(180).pitch(15).build(), lastState().spawn());
	}

	@Test
	void emptiesTheWorldStateWhenTheConnectionEnds() {
		connect();
		player.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		player.deliver(ClientboundSetTime.builder().gameTime(1).dayTime(1L).tickDayTime(true).build());

		player.disconnect();

		assertEquals(WorldState.EMPTY, lastState());
	}

	private WorldState lastState() {
		List<WorldState> states = player.emitted(WorldChannel.STATE);
		return states.get(states.size() - 1);
	}

	private void connect() {
		extension.bind(player, operations);
		player.connect();
	}

	private static ClientboundLogin login(int entityId, String dimension, DimensionType type) {
		return ClientboundLogin.builder()
				.entityId(new EntityId(entityId))
				.level(OVERWORLD)
				.level(NETHER)
				.dimension(dimension)
				.dimensionType(type)
				.build();
	}

	private static ClientboundRespawn respawn(String dimension, DimensionType type) {
		return ClientboundRespawn.builder().dimension(dimension).dimensionType(type).build();
	}

	private static DimensionType vanilla(String key) {
		return DimensionType.known(key);
	}

	/**
	 * A change into a vanilla dimension of normal generation, with the type's block heights from Minecraft 1.20.5 on.
	 */
	private static DimensionChange change(String from, String to, Environment environment) {
		DimensionType type = vanilla(to);
		Level level = new Level(to, levelType(to, environment, type.minY(), type.height()),
				LevelGeneration.NORMAL);

		return new DimensionChange(from, to, level);
	}

	/**
	 * Creates the World capability's dimension type, whose name the protocol's own dimension type shares.
	 */
	private static me.whereareiam.anvil.capability.world.model.level.DimensionType levelType(
			String key,
			Environment environment,
			int minY,
			int height
	) {
		return new me.whereareiam.anvil.capability.world.model.level.DimensionType(key, environment, minY, height);
	}
}
