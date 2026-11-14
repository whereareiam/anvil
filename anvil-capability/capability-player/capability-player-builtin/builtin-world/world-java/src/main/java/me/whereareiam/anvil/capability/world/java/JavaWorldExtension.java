package me.whereareiam.anvil.capability.world.java;

import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.ClockInfo;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.capability.world.model.DimensionTypeInfo;
import me.whereareiam.anvil.capability.world.model.LevelInfo;
import me.whereareiam.anvil.capability.world.model.ServerInfo;
import me.whereareiam.anvil.capability.world.model.SpawnPoint;
import me.whereareiam.anvil.capability.world.model.Weather;
import me.whereareiam.anvil.capability.world.model.WorldBorder;
import me.whereareiam.anvil.capability.world.model.WorldState;
import me.whereareiam.anvil.capability.world.model.WorldTime;
import me.whereareiam.anvil.capability.world.type.Environment;
import me.whereareiam.anvil.capability.world.type.LevelGeneration;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ClockState;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.protocol.java.api.type.CardinalLight;
import me.whereareiam.anvil.protocol.java.api.type.Skybox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Installs the built-in world capability for players that speak the Java Edition protocol: it emits the server each
 * join of the player's current connection names, the level each join and respawn puts the player in, together with
 * the dimension it was in, and the dimension the connection was in when it ends. Each connection starts in no level.
 * The world registers no operations.
 *
 * <p>It also emits the whole world state each time the connection is told about the time, the weather, the world
 * border or the spawn point, as the client keeps them: a join starts a fresh state, a respawn into another dimension
 * starts a level with clear weather and no border yet, and the end of the connection empties the state. From
 * Minecraft 26.1 the time of day is the time of the clock the level's dimension type follows; before, the server
 * sends it. A spawn point of a release that names no level is in the level the player is in.</p>
 */
public final class JavaWorldExtension implements WorkerExtension<Object> {
	/**
	 * The border of a level the server has not described yet, as the game starts one.
	 */
	private static final WorldBorder DEFAULT_BORDER = WorldBorder.builder()
			.size(5.9999968E7)
			.lerpFrom(5.9999968E7)
			.absoluteMaxSize(29999984)
			.warningBlocks(5)
			.warningTime(15)
			.build();

	@Override
	public @NotNull String id() {
		return WorldChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		Dimension dimension = new Dimension(player);

		Subscription connections = player.bindConnection(() -> dimension::left);
		Subscription binding = Subscription.all(
				protocol.listen(ClientboundLogin.class, dimension::joined),
				protocol.listen(ClientboundRespawn.class, dimension::respawned),
				protocol.listen(ClientboundSetTime.class, dimension::time),
				protocol.listen(ClientboundGameEvent.class, dimension::event),
				protocol.listen(ClientboundInitializeBorder.class, dimension::border),
				protocol.listen(ClientboundSetBorderCenter.class, center -> dimension.border(border -> border.toBuilder()
						.centerX(center.getCenterX())
						.centerZ(center.getCenterZ())
						.build())),
				protocol.listen(ClientboundSetBorderLerpSize.class, lerp -> dimension.border(border -> border.toBuilder()
						.size(lerp.getNewSize())
						.lerpFrom(lerp.getOldSize())
						.lerpTicks(lerp.getLerpTime())
						.build())),
				protocol.listen(ClientboundSetBorderSize.class, size -> dimension.border(border -> border.toBuilder()
						.size(size.getSize())
						.lerpFrom(size.getSize())
						.lerpTicks(0)
						.build())),
				protocol.listen(ClientboundSetBorderWarningDelay.class, delay -> dimension.border(border -> border.toBuilder()
						.warningTime(delay.getWarningDelay())
						.build())),
				protocol.listen(ClientboundSetBorderWarningDistance.class, distance -> dimension.border(border -> border.toBuilder()
						.warningBlocks(distance.getWarningBlocks())
						.build())),
				protocol.listen(ClientboundSetDefaultSpawnPosition.class, dimension::spawn),
				dimension::discard,
				connections
		);

		return binding::close;
	}

	/**
	 * The level the player's current connection is in and what the connection was told about the world around it. The
	 * packet thread of the connection enters levels and changes the state, while the connection's end may arrive from
	 * another thread.
	 */
	private static final class Dimension {
		private final PlayerBindingContext<Object> player;
		private @Nullable LevelInfo current;
		private @Nullable DimensionType type;
		private boolean observed = true;

		private WorldState state = WorldState.EMPTY;
		private @Nullable ClientboundSetTime lastTime;
		private final Map<String, ClockInfo> clocks = new LinkedHashMap<>();

		private Dimension(PlayerBindingContext<Object> player) {
			this.player = player;
		}

		private synchronized void joined(ClientboundLogin login) {
			lastTime = null;
			clocks.clear();
			state = WorldState.EMPTY;
			if (observed)
				player.emit(WorldChannel.SERVER, ServerInfo.builder()
						.hardcore(login.isHardcore())
						.levels(login.getLevels())
						.build());
			entered(login.getDimension(), login.getDimensionType(), login.isDebug(), login.isFlat());
		}

		private synchronized void respawned(ClientboundRespawn respawn) {
			entered(respawn.getDimension(), respawn.getDimensionType(), respawn.isDebug(), respawn.isFlat());
		}

		private void entered(String dimension, DimensionType type, boolean debug, boolean flat) {
			if (!observed) return;

			LevelInfo level = new LevelInfo(dimension, new DimensionTypeInfo(type.getKey(), environment(type)),
					generation(debug, flat));
			player.emit(WorldChannel.DIMENSION, new DimensionChange(current == null ? null : current.getDimension(),
					dimension, level));
			boolean anotherLevel = current == null || !current.getDimension().equals(dimension);
			current = level;
			this.type = type;

			WorldState.WorldStateBuilder next = state.toBuilder().time(time());
			if (anotherLevel) next.weather(Weather.CLEAR).border(null);
			emit(next.build());
		}

		private synchronized void time(ClientboundSetTime time) {
			if (!observed || current == null) return;

			lastTime = time;
			for (Map.Entry<String, ClockState> update : time.getClockUpdates().entrySet())
				clocks.put(update.getKey(), new ClockInfo(update.getValue().getTotalTicks(), update.getValue().getRate()));
			emit(state.toBuilder().time(time()).build());
		}

		/**
		 * Tells the time of the level the player is in: the time of day the server sent before Minecraft 26.1, and from
		 * then the time of the clock the level's dimension type follows.
		 */
		private @Nullable WorldTime time() {
			ClientboundSetTime time = lastTime;
			if (time == null) return null;
			if (time.getDayTime() != null)
				return WorldTime.builder()
						.gameTime(time.getGameTime())
						.dayTime(time.getDayTime())
						.advancing(time.isTickDayTime())
						.build();

			String clockKey = type == null ? null : type.getDefaultClock();
			ClockInfo clock = clockKey == null ? null : clocks.get(clockKey);
			return WorldTime.builder()
					.gameTime(time.getGameTime())
					.dayTime(clock == null ? null : clock.getTicks())
					.advancing(clock != null && clock.getRate() != 0)
					.clocks(clocks)
					.build();
		}

		/**
		 * Follows the weather events as the client does: the rain and thunder levels, and a rain level that starting or
		 * stopping rain resets to where the following changes begin. Other game events leave the world as it is.
		 */
		private synchronized void event(ClientboundGameEvent event) {
			if (!observed || current == null) return;

			Weather weather = state.getWeather() == null ? Weather.CLEAR : state.getWeather();
			Weather next = switch (event.getEvent()) {
				case START_RAINING -> weather.toBuilder().rainLevel(0).build();
				case STOP_RAINING -> weather.toBuilder().rainLevel(1).build();
				case RAIN_LEVEL_CHANGE -> weather.toBuilder().rainLevel(event.getValue()).build();
				case THUNDER_LEVEL_CHANGE -> weather.toBuilder().thunderLevel(event.getValue()).build();
				default -> null;
			};
			if (next != null) emit(state.toBuilder().weather(next).build());
		}

		private synchronized void border(ClientboundInitializeBorder border) {
			if (!observed || current == null) return;

			emit(state.toBuilder().border(WorldBorder.builder()
					.centerX(border.getCenterX())
					.centerZ(border.getCenterZ())
					.size(border.getNewSize())
					.lerpFrom(border.getOldSize())
					.lerpTicks(border.getLerpTime())
					.absoluteMaxSize(border.getAbsoluteMaxSize())
					.warningBlocks(border.getWarningBlocks())
					.warningTime(border.getWarningTime())
					.build()).build());
		}

		/**
		 * Changes a part of the level's border, which starts as the game's default border when the server changes a
		 * part before describing the whole border.
		 */
		private synchronized void border(UnaryOperator<WorldBorder> change) {
			if (!observed || current == null) return;

			WorldBorder border = state.getBorder() == null ? DEFAULT_BORDER : state.getBorder();
			emit(state.toBuilder().border(change.apply(border)).build());
		}

		private synchronized void spawn(ClientboundSetDefaultSpawnPosition spawn) {
			if (!observed || current == null) return;

			BlockPosition position = spawn.getPosition();
			emit(state.toBuilder().spawn(SpawnPoint.builder()
					.dimension(spawn.getDimension() == null ? current.getDimension() : spawn.getDimension())
					.x(position.getX())
					.y(position.getY())
					.z(position.getZ())
					.yaw(spawn.getYaw())
					.pitch(spawn.getPitch())
					.build()).build());
		}

		private void emit(WorldState next) {
			state = next;
			player.emit(WorldChannel.STATE, next);
		}

		private synchronized void left() {
			LevelInfo left = current;
			current = null;
			type = null;
			lastTime = null;
			clocks.clear();
			boolean known = state != WorldState.EMPTY;
			state = WorldState.EMPTY;
			if (!observed) return;
			if (known) player.emit(WorldChannel.STATE, WorldState.EMPTY);
			if (left != null) player.emit(WorldChannel.LEFT, left.getDimension());
		}

		/**
		 * Stops emitting, for a player whose world is no longer observed.
		 */
		private synchronized void discard() {
			observed = false;
			current = null;
			state = WorldState.EMPTY;
		}

		private static LevelGeneration generation(boolean debug, boolean flat) {
			if (debug) return LevelGeneration.DEBUG;

			return flat ? LevelGeneration.FLAT : LevelGeneration.NORMAL;
		}

		/**
		 * Tells the environment a type's sky and shading render: those of the overworld, the nether or the end, or any
		 * other pair, such as a mod's.
		 */
		private static Environment environment(DimensionType type) {
			if (type.getSkybox() == Skybox.OVERWORLD && type.getCardinalLight() == CardinalLight.DEFAULT)
				return Environment.OVERWORLD;
			if (type.getSkybox() == Skybox.NONE && type.getCardinalLight() == CardinalLight.NETHER) return Environment.NETHER;
			if (type.getSkybox() == Skybox.END && type.getCardinalLight() == CardinalLight.DEFAULT) return Environment.END;

			return Environment.CUSTOM;
		}
	}
}
