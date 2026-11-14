package me.whereareiam.anvil.capability.worlds.neoforge.v1_21_11;

import me.whereareiam.anvil.capability.worlds.agent.ServerWorlds;
import me.whereareiam.anvil.capability.worlds.agent.WorldChangeLog;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The levels of a NeoForge server of Minecraft 1.21.11 and later, named by their keys. A level's spawn is the respawn data the
 * server reports for it, and its kind follows its dimension type. A teleport reports whether the server moved the
 * player.
 */
final class NeoForgeWorlds implements ServerWorlds {
	private final MinecraftServer server;
	private final WorldChangeLog changes;

	NeoForgeWorlds(@NotNull MinecraftServer server, @NotNull WorldChangeLog changes) {
		this.server = server;
		this.changes = changes;
	}

	@Override
	public @NotNull List<AgentWorld> worlds() {
		List<AgentWorld> worlds = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels())
			worlds.add(AgentWorld.builder().key(key(level.dimension())).kind(kind(level.dimensionTypeRegistration()))
					.spawn(spawn(level)).build());

		return worlds;
	}

	@Override
	public @NotNull List<AgentPlacement> placements() {
		return server.getPlayerList().getPlayers().stream()
				.map(player -> AgentPlacement.builder()
						.uniqueId(player.getUUID())
						.location(AgentLocation.builder()
								.world(key(player.level().dimension()))
								.x(player.getX())
								.y(player.getY())
								.z(player.getZ())
								.yaw(player.getYRot())
								.pitch(player.getXRot())
								.build())
						.build())
				.toList();
	}

	@Override
	public boolean teleport(@NotNull UUID uniqueId, @NotNull AgentLocation location) {
		ServerPlayer player = server.getPlayerList().getPlayer(uniqueId);
		if (player == null) throw new IllegalArgumentException("No player " + uniqueId + " is on the server");

		ServerLevel level = level(location.getWorld());
		return player.teleportTo(level, location.getX(), location.getY(), location.getZ(), Set.of(), location.getYaw(),
				location.getPitch(), true);
	}

	/**
	 * Records a player's dimension change.
	 *
	 * @param event the change
	 */
	void changed(@NotNull PlayerEvent.PlayerChangedDimensionEvent event) {
		changes.record(event.getEntity().getUUID(), event.getEntity().getScoreboardName(), key(event.getFrom()),
				key(event.getTo()));
	}

	private @NotNull ServerLevel level(@NotNull String key) {
		for (ServerLevel level : server.getAllLevels())
			if (key(level.dimension()).equals(key)) return level;

		throw new IllegalArgumentException("The server has not loaded the world " + key);
	}

	private static @NotNull AgentLocation spawn(@NotNull ServerLevel level) {
		LevelData.RespawnData spawn = level.getRespawnData();
		return AgentLocation.builder()
				.world(key(level.dimension()))
				.x(spawn.pos().getX() + 0.5)
				.y(spawn.pos().getY())
				.z(spawn.pos().getZ() + 0.5)
				.yaw(spawn.yaw())
				.pitch(spawn.pitch())
				.build();
	}

	private static @NotNull String key(@NotNull ResourceKey<Level> dimension) {
		return dimension.identifier().toString();
	}

	private static @NotNull String kind(@NotNull Holder<DimensionType> type) {
		if (type.is(BuiltinDimensionTypes.OVERWORLD) || type.is(BuiltinDimensionTypes.OVERWORLD_CAVES)) return "OVERWORLD";
		if (type.is(BuiltinDimensionTypes.NETHER)) return "NETHER";
		if (type.is(BuiltinDimensionTypes.END)) return "END";

		return "CUSTOM";
	}
}
