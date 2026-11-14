package me.whereareiam.anvil.capability.world.java;

import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Installs the built-in world capability for players that speak the Java Edition protocol: it emits the dimension
 * each level the player's current connection joins or respawns in, together with the dimension it was in, and the
 * dimension the connection was in when it ends. Each connection starts in no dimension. The world registers no
 * operations.
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
				protocol.listen(ClientboundLogin.class, login -> dimension.entered(login.getDimension())),
				protocol.listen(ClientboundRespawn.class, respawn -> dimension.entered(respawn.getDimension())),
				dimension::discard,
				connections
		);

		return binding::close;
	}

	/**
	 * The dimension the player's current connection is in. The packet thread of the connection enters dimensions,
	 * while the connection's end may arrive from another thread.
	 */
	private static final class Dimension {
		private final PlayerBindingContext<Object> player;
		private @Nullable String current;
		private boolean observed = true;

		private Dimension(PlayerBindingContext<Object> player) {
			this.player = player;
		}

		private synchronized void entered(String dimension) {
			if (!observed) return;

			player.emit(WorldChannel.DIMENSION, new DimensionChange(current, dimension));
			current = dimension;
		}

		private synchronized void left() {
			String left = current;
			current = null;
			if (observed && left != null) player.emit(WorldChannel.LEFT, left);
		}

		/**
		 * Stops emitting, for a player whose world is no longer observed.
		 */
		private synchronized void discard() {
			observed = false;
			current = null;
		}
	}
}
