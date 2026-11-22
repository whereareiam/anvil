package me.whereareiam.anvil.edition.java.protocol.worker.position;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test players of the kit's tests of the client's own player: connected players of a client version that joined the
 * overworld as entity {@link #ENTITY_ID}, whose release carries the tick end from 1.21.2 as the protocols do, and the
 * packets those tests deliver.
 */
final class JoinedPlayers {
	static final EntityId ENTITY_ID = new EntityId(7);
	static final DimensionType OVERWORLD_TYPE = DimensionType.known("minecraft:overworld");
	static final ClientboundGameEvent LOADING_STARTED = new ClientboundGameEvent(GameEventType.LEVEL_CHUNKS_LOAD_START,
			0.0F);
	private static final Map<String, Integer> PROTOCOLS = Map.of("1.16.5", 754, "1.17.1", 756, "1.18.2", 758,
			"1.21.1", 767, "1.21.11", 774, "26.3", 777);

	private JoinedPlayers() {
	}

	/**
	 * Creates a connected player of a client version that joined the overworld.
	 */
	static @NotNull TestJavaPlayer joined(@NotNull String version) {
		TestJavaPlayer player = connected(version);
		player.deliver(ClientboundLogin.builder()
				.entityId(ENTITY_ID)
				.level("minecraft:overworld")
				.dimension("minecraft:overworld")
				.dimensionType(OVERWORLD_TYPE)
				.build());
		return player;
	}

	/**
	 * Creates a connected player of a client version that has not joined a level.
	 */
	static @NotNull TestJavaPlayer connected(@NotNull String version) {
		MinecraftVersion client = MinecraftVersion.parse(version);
		boolean tickEnd = client.isAtLeast(MinecraftVersion.parse("1.21.2"));
		TestJavaPlayer player = new TestJavaPlayer("Alice", client, PROTOCOLS.get(version),
				type -> tickEnd || type != ServerboundClientTickEnd.class);
		player.connect();
		return player;
	}

	static @NotNull ClientboundPlayerPosition teleport(
			@NotNull Vec3d position,
			float yaw,
			float pitch,
			@NotNull Set<Relative> relatives
	) {
		return ClientboundPlayerPosition.builder().position(position).yaw(yaw).pitch(pitch).relatives(relatives).build();
	}

	/**
	 * Delivers the column at a chunk position, all air.
	 */
	static void load(@NotNull TestJavaPlayer player, int chunkX, int chunkZ) {
		player.deliver(new ClientboundLevelChunkWithLight(new ChunkPosition(chunkX, chunkZ), List.of()));
	}
}
