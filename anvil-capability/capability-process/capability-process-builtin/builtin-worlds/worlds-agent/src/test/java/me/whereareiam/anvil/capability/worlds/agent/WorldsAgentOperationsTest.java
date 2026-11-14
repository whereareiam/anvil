package me.whereareiam.anvil.capability.worlds.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLogRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentTransfer;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldEvents;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldLoad;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldOutcome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldsAgentOperationsTest {
	private static final UUID ALICE = new UUID(0, 1);
	private static final AgentLocation SPAWN = location("minecraft:overworld", 0.5, 64, 0.5);
	private static final AgentLocation ARENA = location("minecraft:arena", 10, 70, -4);
	private static final AgentWorldCreation DUNGEON = AgentWorldCreation.builder().key("minecraft:dungeon").kind("NETHER")
			.seed(7L).generator("FLAT").build();

	private final RecordingPlatform platform = new RecordingPlatform();
	private final RecordingRegistry registry = new RecordingRegistry();
	private final FixedWorlds worlds = new FixedWorlds();
	private WorldChangeLog log;
	private WorldEventLog events;

	@Test
	void ownsItsNamespaceAndRegistersEveryOperationOfTheChannel() {
		install();

		assertEquals("me.whereareiam.anvil.worlds", new Operations().id());
		assertEquals(List.of("me.whereareiam.anvil.worlds.worlds", "me.whereareiam.anvil.worlds.placements",
				"me.whereareiam.anvil.worlds.send", "me.whereareiam.anvil.worlds.changes", "me.whereareiam.anvil.worlds.create",
				"me.whereareiam.anvil.worlds.load", "me.whereareiam.anvil.worlds.unload", "me.whereareiam.anvil.worlds.delete", "me.whereareiam.anvil.worlds.events"), List.copyOf(registry.handlers.keySet()));
	}

	@Test
	void answersWorldsPlacementsAndTransfersOnThePlatformsThread() throws Exception {
		install();

		AgentWorld overworld = AgentWorld.builder().key("minecraft:overworld").kind("OVERWORLD").spawn(SPAWN).build();
		assertEquals(AgentWorldList.builder().world(overworld).build(), registry.execute(WorldsOperations.WORLDS, null));
		assertEquals(AgentPlacements.builder().placement(placement(SPAWN)).build(),
				registry.execute(WorldsOperations.PLACEMENTS, null));
		assertTrue(registry.execute(WorldsOperations.SEND, transfer(ARENA)));
		assertEquals(List.of(ARENA), worlds.teleported);
		assertEquals(3, platform.calls);
	}

	@Test
	void readsTheChangesThePlatformSideRecorded() throws Exception {
		install();
		log.record(ALICE, "Alice", "minecraft:overworld", "minecraft:arena");

		AgentWorldChanges changes = registry.execute(WorldsOperations.CHANGES,
				AgentLogRequest.builder().after(0).build());

		assertEquals(1, changes.getChanges().size());
		assertEquals("minecraft:arena", changes.getChanges().getFirst().getTo());
	}

	@Test
	void readsTheWorldEventsThePlatformSideRecorded() throws Exception {
		install();
		events.loaded("minecraft:arena");
		events.unloaded("minecraft:arena");

		AgentWorldEvents read = registry.execute(WorldsOperations.EVENTS, AgentLogRequest.builder().after(1).build());

		assertEquals(2, read.getEvents().size());
		read = registry.execute(WorldsOperations.EVENTS, AgentLogRequest.builder().log(read.getLog()).after(1).build());
		assertEquals(List.of("minecraft:arena", "UNLOADED", 2L), List.of(read.getEvents().getFirst().getWorld(),
				read.getEvents().getFirst().getType(), read.getEvents().getFirst().getPosition()));
	}

	@Test
	void carriesEveryPayloadThroughAPlainJsonMapperAsTheAgentAndTheHostDo() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		List<Object> payloads = List.of(
				AgentWorldList.builder().world(AgentWorld.builder().key("minecraft:arena").kind("CUSTOM").spawn(ARENA).build()).build(),
				AgentPlacements.builder().placement(placement(SPAWN)).build(),
				transfer(ARENA),
				AgentLogRequest.builder().log(null).after(3).build(),
				new WorldChangeLog().since(AgentLogRequest.builder().after(0).build()),
				DUNGEON,
				AgentWorldCreation.builder().key("minecraft:lobby").kind("OVERWORLD").generator("DEFAULT").build(),
				AgentWorldOutcome.builder().world(AgentWorld.builder().key("minecraft:dungeon").kind("NETHER").spawn(ARENA).build())
						.build(),
				AgentWorldOutcome.builder().refusal("refused").build(),
				AgentWorldOutcome.builder().missing("no folder").build(),
				AgentWorldLoad.builder().key("minecraft:map").kind("END").build(),
				recordedEvents()
		);

		for (Object payload : payloads)
			assertEquals(payload, mapper.treeToValue(mapper.valueToTree(payload), payload.getClass()));
	}

	@Test
	void reportsATransferThePlatformRefused() throws Exception {
		install();
		worlds.refuse = true;

		assertFalse(registry.execute(WorldsOperations.SEND, transfer(ARENA)));
	}

	@Test
	void createsAndUnloadsWorldsOnThePlatformsThread() throws Exception {
		install();

		AgentWorld dungeon = AgentWorld.builder().key("minecraft:dungeon").kind("NETHER").spawn(ARENA).build();
		assertEquals(AgentWorldOutcome.builder().world(dungeon).build(), registry.execute(WorldsOperations.CREATE, DUNGEON));
		assertEquals(AgentWorldOutcome.builder().build(), registry.execute(WorldsOperations.UNLOAD, "minecraft:dungeon"));
		assertEquals(AgentWorldOutcome.builder().world(dungeon).build(), registry.execute(WorldsOperations.LOAD,
				AgentWorldLoad.builder().key("minecraft:dungeon").kind("NETHER").build()));
		assertEquals(AgentWorldOutcome.builder().build(), registry.execute(WorldsOperations.DELETE, "minecraft:dungeon"));
		assertEquals(List.of("create minecraft:dungeon", "unload minecraft:dungeon", "load minecraft:dungeon",
				"delete minecraft:dungeon"), worlds.changed);
		assertEquals(4, platform.calls);
	}

	@Test
	void answersACreationOrUnloadingThePlatformCannotDoOrTheServerRefusedWithTheReason() throws Exception {
		install();

		worlds.failure = new UnsupportedOperationException("No API");
		assertEquals(AgentWorldOutcome.builder().unsupported("No API").build(), registry.execute(WorldsOperations.CREATE, DUNGEON));
		worlds.failure = new IllegalStateException("Players are in it");
		assertEquals(AgentWorldOutcome.builder().refusal("Players are in it").build(),
				registry.execute(WorldsOperations.UNLOAD, "minecraft:dungeon"));
		assertEquals(AgentWorldOutcome.builder().refusal("Players are in it").build(),
				registry.execute(WorldsOperations.DELETE, "minecraft:dungeon"));
		worlds.failure = new IllegalArgumentException("No folder");
		assertEquals(AgentWorldOutcome.builder().missing("No folder").build(), registry.execute(WorldsOperations.LOAD,
				AgentWorldLoad.builder().key("minecraft:dungeon").kind("NETHER").build()));
	}

	private static AgentWorldEvents recordedEvents() {
		WorldEventLog log = new WorldEventLog();
		log.loaded("minecraft:arena");
		return log.since(AgentLogRequest.builder().after(0).build());
	}

	private void install() {
		new Operations().install(platform, registry);
	}

	private static AgentPlacement placement(AgentLocation location) {
		return AgentPlacement.builder().uniqueId(ALICE).location(location).build();
	}

	private static AgentTransfer transfer(AgentLocation location) {
		return AgentTransfer.builder().uniqueId(ALICE).location(location).build();
	}

	private static AgentLocation location(String world, double x, double y, double z) {
		return AgentLocation.builder().world(world).x(x).y(y).z(z).build();
	}

	private final class Operations extends WorldsAgentOperations {
		@Override
		protected @NotNull ServerWorlds open(
				@NotNull PlatformAgent platform,
				@NotNull WorldChangeLog changes,
				@NotNull WorldEventLog events
		) {
			log = changes;
			WorldsAgentOperationsTest.this.events = events;
			return worlds;
		}
	}

	private static final class FixedWorlds implements ServerWorlds {
		private final List<AgentLocation> teleported = new ArrayList<>();
		private final List<String> changed = new ArrayList<>();
		private boolean refuse;
		private RuntimeException failure;

		@Override
		public @NotNull List<AgentWorld> worlds() {
			return List.of(AgentWorld.builder().key("minecraft:overworld").kind("OVERWORLD").spawn(SPAWN).build());
		}

		@Override
		public @NotNull List<AgentPlacement> placements() {
			return List.of(placement(SPAWN));
		}

		@Override
		public boolean teleport(@NotNull UUID uniqueId, @NotNull AgentLocation location) {
			if (refuse) return false;

			teleported.add(location);
			return true;
		}

		@Override
		public @NotNull AgentWorld create(@NotNull AgentWorldCreation creation) {
			if (failure != null) throw failure;

			changed.add("create " + creation.getKey());
			return AgentWorld.builder().key(creation.getKey()).kind(creation.getKind()).spawn(ARENA).build();
		}

		@Override
		public @NotNull AgentWorld load(@NotNull AgentWorldLoad load) {
			if (failure != null) throw failure;

			changed.add("load " + load.getKey());
			return AgentWorld.builder().key(load.getKey()).kind(load.getKind()).spawn(ARENA).build();
		}

		@Override
		public void unload(@NotNull String key) {
			if (failure != null) throw failure;

			changed.add("unload " + key);
		}

		@Override
		public void delete(@NotNull String key) {
			if (failure != null) throw failure;

			changed.add("delete " + key);
		}
	}

	/**
	 * Counts the calls made on the platform's thread.
	 */
	private static final class RecordingPlatform implements PlatformAgent {
		private int calls;

		@Override
		public @NotNull AgentInfo info() {
			return AgentInfo.builder().platform("Paper").version("1.21.11").role(AgentRole.SERVER).build();
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			throw new AssertionError("The worlds use no commands");
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
