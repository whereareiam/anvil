package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.worlds.agent.WorldChangeLog;
import me.whereareiam.anvil.capability.worlds.agent.WorldsOperations;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentTransfer;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChangesRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldOutcome;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldChange;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import me.whereareiam.anvil.capability.worlds.type.WorldGenerator;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldsTest {
	private static final Duration WAIT = Duration.ofMillis(200);
	private static final AgentLocation OVERWORLD_SPAWN = location("minecraft:overworld", 0.5, 64, 0.5);
	private static final AgentLocation NETHER_SPAWN = location("minecraft:the_nether", 8, 70, 8);
	private static final AgentLocation ARENA_SPAWN = location("minecraft:arena", -3, 80, 12);

	private final PlayerProfile offline = new PlayerProfile("Alice", UUID.randomUUID());
	private final PlayerProfile forwarded = new PlayerProfile("Alice", UUID.randomUUID());
	private final PlayerProfile bob = new PlayerProfile("Bob", UUID.randomUUID());
	private final SimulatedPlayer alice = player("Alice", offline, forwarded);
	private final FakeServer server = new FakeServer();
	private final AgentWorlds worlds = new AgentWorlds("lobby", server);

	@Test
	void listsTheServersWorldsWithTheirKindsAndSpawns() {
		assertEquals(List.of(
				world("minecraft:overworld", WorldKind.OVERWORLD, OVERWORLD_SPAWN),
				world("minecraft:the_nether", WorldKind.NETHER, NETHER_SPAWN),
				world("minecraft:arena", WorldKind.CUSTOM, ARENA_SPAWN)
		), worlds.worlds());
		assertEquals(Optional.of(world("minecraft:arena", WorldKind.CUSTOM, ARENA_SPAWN)), worlds.world("arena"));
		assertEquals(Optional.empty(), worlds.world("minecraft:the_end"));
	}

	@Test
	void locatesAPlayerByAnyProfileItsClientWasGranted() {
		server.placements.put(forwarded.getUniqueId(), NETHER_SPAWN);
		server.placements.put(bob.getUniqueId(), OVERWORLD_SPAWN);

		assertEquals(Optional.of(location(NETHER_SPAWN)), worlds.location(alice));
		assertEquals(Optional.empty(), worlds.location(player("Carol")));
	}

	@Test
	void sendsAPlayerToAWorldsSpawnOrToALocationUnderTheUuidTheServerKnows() {
		server.placements.put(forwarded.getUniqueId(), OVERWORLD_SPAWN);

		worlds.send(alice, "the_nether");
		worlds.send(alice, WorldLocation.builder().world("arena").x(1).y(81).z(2).yaw(90).pitch(10).build());

		assertEquals(List.of(
				AgentTransfer.builder().uniqueId(forwarded.getUniqueId()).location(NETHER_SPAWN).build(),
				AgentTransfer.builder().uniqueId(forwarded.getUniqueId()).location(AgentLocation.builder()
						.world("minecraft:arena").x(1).y(81).z(2).yaw(90).pitch(10).build()).build()
		), server.transfers);
	}

	@Test
	void refusesToSendToAWorldTheServerHasNotLoadedOrAPlayerItDoesNotHave() {
		server.placements.put(forwarded.getUniqueId(), OVERWORLD_SPAWN);

		assertEquals("'lobby' has not loaded the world 'minecraft:the_end'; it has [minecraft:overworld, "
				+ "minecraft:the_nether, minecraft:arena]", assertThrows(IllegalArgumentException.class,
				() -> worlds.send(alice, "the_end")).getMessage());
		assertEquals("Player 'Carol' is not on 'lobby'", assertThrows(IllegalStateException.class,
				() -> worlds.send(player("Carol"), "arena")).getMessage());
		assertTrue(server.transfers.isEmpty());
	}

	@Test
	void failsWhenTheServerRefusedTheTransfer() {
		server.placements.put(forwarded.getUniqueId(), OVERWORLD_SPAWN);
		server.refuse = true;

		assertEquals("'lobby' refused to move player 'Alice' to 'minecraft:arena'",
				assertThrows(IllegalStateException.class, () -> worlds.send(alice, "arena")).getMessage());
	}

	@Test
	void createsAWorldUnderItsFullKeyAndReturnsItAsTheServerReportsIt() {
		AgentLocation spawn = location("minecraft:arena_nether", 0.5, 64, 0.5);
		server.outcome = AgentWorldOutcome.builder()
				.world(AgentWorld.builder().key("minecraft:arena_nether").kind("NETHER").spawn(spawn).build())
				.build();

		ServerWorld created = worlds.create(WorldCreation.builder()
				.key("arena_nether")
				.kind(WorldKind.NETHER)
				.seed(42L)
				.generator(WorldGenerator.EMPTY)
				.build());

		assertEquals(world("minecraft:arena_nether", WorldKind.NETHER, spawn), created);
		assertEquals(List.of(AgentWorldCreation.builder().key("minecraft:arena_nether").kind("NETHER").seed(42L)
				.generator("EMPTY").build()), server.requests);
	}

	@Test
	void createsAnOverworldOfTheDefaultGeneratorWithASeedTheServerPicksUnlessToldOtherwise() {
		server.outcome = AgentWorldOutcome.builder()
				.world(AgentWorld.builder().key("minecraft:lobby").kind("OVERWORLD").spawn(OVERWORLD_SPAWN).build())
				.build();

		worlds.create(WorldCreation.builder().key("minecraft:lobby").build());

		assertEquals(List.of(AgentWorldCreation.builder().key("minecraft:lobby").kind("OVERWORLD").generator("DEFAULT")
				.build()), server.requests);
	}

	@Test
	void refusesACustomWorldAndAWorldTheServerHasAlreadyLoadedWithoutAskingIt() {
		assertEquals("A server creates an OVERWORLD, NETHER or END world, not a CUSTOM one",
				assertThrows(IllegalArgumentException.class, () -> worlds.create(WorldCreation.builder().key("dungeon")
						.kind(WorldKind.CUSTOM).build())).getMessage());
		assertEquals("'lobby' has already loaded the world 'minecraft:arena'", assertThrows(IllegalArgumentException.class,
				() -> worlds.create(WorldCreation.builder().key("arena").build())).getMessage());
		assertTrue(server.requests.isEmpty());
	}

	@Test
	void tellsAWorldThePlatformCannotCreateFromOneTheServerRefused() {
		WorldCreation dungeon = WorldCreation.builder().key("dungeon").build();

		server.outcome = AgentWorldOutcome.builder().unsupported("NeoForge has no API").build();
		assertEquals("'lobby' cannot create the world 'minecraft:dungeon': NeoForge has no API",
				assertThrows(UnsupportedOperationException.class, () -> worlds.create(dungeon)).getMessage());
		server.outcome = AgentWorldOutcome.builder().refusal("The server did not create the world").build();
		assertEquals("'lobby' refused to create the world 'minecraft:dungeon': The server did not create the world",
				assertThrows(IllegalStateException.class, () -> worlds.create(dungeon)).getMessage());
	}

	@Test
	void unloadsALoadedWorldByItsFullKeyAndRefusesOneTheServerHasNotLoaded() {
		server.outcome = AgentWorldOutcome.builder().build();

		worlds.unload("arena");
		assertEquals(List.of("minecraft:arena"), server.requests);

		assertThrows(IllegalArgumentException.class, () -> worlds.unload("the_end"));
		server.outcome = AgentWorldOutcome.builder().refusal("Players are in minecraft:arena: [Alice]").build();
		assertEquals("'lobby' refused to unload the world 'minecraft:arena': Players are in minecraft:arena: [Alice]",
				assertThrows(IllegalStateException.class, () -> worlds.unload("arena")).getMessage());
		assertEquals(2, server.requests.size());
	}

	@Test
	void watchesOnlyChangesSinceItForTheMatchingPlayer() {
		server.log.record(forwarded.getUniqueId(), "Alice", "minecraft:overworld", "minecraft:arena");
		WorldChangeWatch changes = worlds.watch();

		server.log.record(bob.getUniqueId(), "Bob", "minecraft:overworld", "minecraft:arena");
		assertThrows(IllegalStateException.class, () -> changes.changed(alice, "arena", WAIT));

		server.log.record(forwarded.getUniqueId(), "Alice", "minecraft:arena", "minecraft:the_nether");
		WorldChange change = changes.changed(alice, "minecraft:the_nether", WAIT);
		assertEquals(forwarded, change.getProfile());
		assertEquals("minecraft:arena", change.getFrom());
		assertEquals(List.of(), changes.unread());
	}

	@Test
	void keepsTheChangesOfARestartedServerFollowedByThoseOfItsNewProcess() {
		WorldChangeWatch changes = worlds.watch();
		server.log.record(forwarded.getUniqueId(), "Alice", "minecraft:overworld", "minecraft:arena");
		assertEquals("minecraft:arena", changes.changed(alice, WAIT).getTo());

		server.log = new WorldChangeLog();
		server.log.record(forwarded.getUniqueId(), "Alice", "minecraft:overworld", "minecraft:the_nether");

		assertEquals("minecraft:the_nether", changes.changed(alice, WAIT).getTo());
	}

	@Test
	void failsAsSoonAsAForbiddenMoveHappenedAndExpectsAMove() {
		WorldChangeWatch changes = worlds.watch();
		Expectation arena = changes.expectChanged(alice, "arena");

		changes.neverChanged(alice, WAIT);
		assertFalse(arena.met());

		server.log.record(forwarded.getUniqueId(), "Alice", "minecraft:overworld", "minecraft:arena");
		assertTrue(arena.met());
		assertEquals("'lobby' moved player 'Alice' from 'minecraft:overworld' to 'minecraft:arena', which it must not do",
				assertThrows(IllegalStateException.class, () -> changes.neverChanged(alice, WAIT)).getMessage());
	}

	private static ServerWorld world(String key, WorldKind kind, AgentLocation spawn) {
		return ServerWorld.builder().key(key).kind(kind).spawn(location(spawn)).build();
	}

	private static WorldLocation location(AgentLocation location) {
		return WorldLocation.builder().world(location.getWorld()).x(location.getX()).y(location.getY()).z(location.getZ())
				.yaw(location.getYaw()).pitch(location.getPitch()).build();
	}

	private static AgentLocation location(String world, double x, double y, double z) {
		return AgentLocation.builder().world(world).x(x).y(y).z(z).build();
	}

	/**
	 * A server whose agent answers the worlds operations from fixed worlds, its players' placements and a change log
	 * a test can replace, as a restarted process does.
	 */
	private static final class FakeServer implements RequestChannel {
		private final Map<UUID, AgentLocation> placements = new LinkedHashMap<>();
		private final List<AgentTransfer> transfers = new ArrayList<>();
		private final List<Object> requests = new ArrayList<>();
		private WorldChangeLog log = new WorldChangeLog();
		private boolean refuse;
		private AgentWorldOutcome outcome;

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> operation, @Nullable Q request) {
			Object response = switch (operation.getId()) {
				case "me.whereareiam.anvil.worlds.worlds" -> AgentWorldList.builder()
						.world(AgentWorld.builder().key("minecraft:overworld").kind("OVERWORLD").spawn(OVERWORLD_SPAWN).build())
						.world(AgentWorld.builder().key("minecraft:the_nether").kind("NETHER").spawn(NETHER_SPAWN).build())
						.world(AgentWorld.builder().key("minecraft:arena").kind("CUSTOM").spawn(ARENA_SPAWN).build())
						.build();
				case "me.whereareiam.anvil.worlds.placements" -> AgentPlacements.builder()
						.placements(placements.entrySet().stream()
								.map(entry -> AgentPlacement.builder().uniqueId(entry.getKey()).location(entry.getValue()).build())
								.toList())
						.build();
				case "me.whereareiam.anvil.worlds.send" -> {
					if (refuse) yield false;

					transfers.add((AgentTransfer) request);
					yield true;
				}
				case "me.whereareiam.anvil.worlds.changes" -> log.since((AgentWorldChangesRequest) request);
				case "me.whereareiam.anvil.worlds.create", "me.whereareiam.anvil.worlds.unload" -> {
					requests.add(request);
					yield outcome;
				}
				default -> throw new AssertionError("Unknown operation " + operation.getId());
			};
			assertEquals(WorldsOperations.NAMESPACE, operation.getId().substring(0, operation.getId().lastIndexOf('.')));

			return operation.getResponseType().cast(response);
		}
	}

	/**
	 * A simulated player that was granted the given profiles.
	 */
	private static SimulatedPlayer player(@NotNull String name, @NotNull PlayerProfile... granted) {
		return new SimulatedPlayer() {
			@Override
			public @NotNull String name() {
				return name;
			}

			@Override
			public @NotNull String clientVersion() {
				return "1.21.11";
			}

			@Override
			public @NotNull PlayerProfile requestedProfile() {
				return new PlayerProfile(name, UUID.nameUUIDFromBytes(name.getBytes()));
			}

			@Override
			public @Nullable PlayerProfile grantedProfile() {
				return granted.length == 0 ? null : granted[granted.length - 1];
			}

			@Override
			public @NotNull List<PlayerProfile> grantedProfiles() {
				return List.of(granted);
			}

			@Override
			public @NotNull <C extends PlayerCapability> C capability(@NotNull Class<C> type) {
				throw new AssertionError("The worlds capability uses no player capability");
			}

			@Override
			public boolean hasCapability(@NotNull Class<? extends PlayerCapability> type) {
				return false;
			}

			@Override
			public int awaitFirst(@NotNull Duration timeout, @NotNull Expectation... expectations) {
				throw new AssertionError("The worlds capability awaits nothing on the player");
			}

			@Override
			public @NotNull PlayerState state() {
				return PlayerState.builder().build();
			}

			@Override
			public void destroy() {
				throw new AssertionError("The worlds capability destroys no player");
			}
		};
	}
}
