package me.whereareiam.anvil.capability.worlds.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentTransfer;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChangesRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldsAgentOperationsTest {
	private static final UUID ALICE = new UUID(0, 1);
	private static final AgentLocation SPAWN = location("minecraft:overworld", 0.5, 64, 0.5);
	private static final AgentLocation ARENA = location("minecraft:arena", 10, 70, -4);

	private final RecordingPlatform platform = new RecordingPlatform();
	private final RecordingRegistry registry = new RecordingRegistry();
	private final FixedWorlds worlds = new FixedWorlds();
	private WorldChangeLog log;

	@Test
	void ownsItsNamespaceAndRegistersEveryOperationOfTheChannel() {
		install();

		assertEquals("me.whereareiam.anvil.worlds", new Operations().id());
		assertEquals(List.of("me.whereareiam.anvil.worlds.worlds", "me.whereareiam.anvil.worlds.placements",
				"me.whereareiam.anvil.worlds.send", "me.whereareiam.anvil.worlds.changes"), List.copyOf(registry.handlers.keySet()));
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
				AgentWorldChangesRequest.builder().after(0).build());

		assertEquals(1, changes.getChanges().size());
		assertEquals("minecraft:arena", changes.getChanges().getFirst().getTo());
	}

	@Test
	void carriesEveryPayloadThroughAPlainJsonMapperAsTheAgentAndTheHostDo() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		List<Object> payloads = List.of(
				AgentWorldList.builder().world(AgentWorld.builder().key("minecraft:arena").kind("CUSTOM").spawn(ARENA).build()).build(),
				AgentPlacements.builder().placement(placement(SPAWN)).build(),
				transfer(ARENA),
				AgentWorldChangesRequest.builder().log(null).after(3).build(),
				new WorldChangeLog().since(AgentWorldChangesRequest.builder().after(0).build())
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
		protected @NotNull ServerWorlds open(@NotNull PlatformAgent platform, @NotNull WorldChangeLog changes) {
			log = changes;
			return worlds;
		}
	}

	private static final class FixedWorlds implements ServerWorlds {
		private final List<AgentLocation> teleported = new ArrayList<>();
		private boolean refuse;

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
