package me.whereareiam.anvil.capability.world.java;

import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.capability.world.model.LevelInfo;
import me.whereareiam.anvil.capability.world.type.Environment;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.type.CardinalLight;
import me.whereareiam.anvil.protocol.java.api.type.Skybox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Installs the built-in world capability for players that speak the Java Edition protocol: it emits the level each
 * join and respawn of the player's current connection puts the player in, together with the dimension it was in, and
 * the dimension the connection was in when it ends. A respawn keeps the server's hardcore mode and levels its last
 * join named. Each connection starts in no level. The world registers no operations.
 */
public final class JavaWorldExtension implements WorkerExtension<Object> {
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
				dimension::discard,
				connections
		);

		return binding::close;
	}

	/**
	 * The level the player's current connection is in. The packet thread of the connection enters levels, while the
	 * connection's end may arrive from another thread.
	 */
	private static final class Dimension {
		private final PlayerBindingContext<Object> player;
		private @Nullable LevelInfo current;
		private boolean hardcore;
		private List<String> levels = List.of();
		private boolean observed = true;

		private Dimension(PlayerBindingContext<Object> player) {
			this.player = player;
		}

		private synchronized void joined(ClientboundLogin login) {
			hardcore = login.isHardcore();
			levels = login.getLevels();
			entered(login.getDimension(), login.getDimensionType(), login.isDebug(), login.isFlat());
		}

		private synchronized void respawned(ClientboundRespawn respawn) {
			entered(respawn.getDimension(), respawn.getDimensionType(), respawn.isDebug(), respawn.isFlat());
		}

		private void entered(String dimension, DimensionType type, boolean debug, boolean flat) {
			if (!observed) return;

			LevelInfo level = LevelInfo.builder()
					.dimension(dimension)
					.dimensionType(type.getKey())
					.environment(environment(type))
					.hardcore(hardcore)
					.debug(debug)
					.flat(flat)
					.levels(levels)
					.build();
			player.emit(WorldChannel.DIMENSION, new DimensionChange(current == null ? null : current.getDimension(),
					dimension, level));
			current = level;
		}

		private synchronized void left() {
			LevelInfo left = current;
			current = null;
			levels = List.of();
			if (observed && left != null) player.emit(WorldChannel.LEFT, left.getDimension());
		}

		/**
		 * Stops emitting, for a player whose world is no longer observed.
		 */
		private synchronized void discard() {
			observed = false;
			current = null;
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
