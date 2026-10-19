package me.whereareiam.anvil.capability.movement.java;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.java.physics.ClientMovement;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Installs the built-in movement capability for players that speak the Java Edition protocol: moves and turns the
 * test sends, the answer to every server teleport, and the vanilla client's physics, whose ticks run on one daemon
 * thread that every player of the worker shares.
 */
public final class JavaMovementExtension implements WorkerExtension<Object> {
	private static final long TICK_MILLIS = 50L;

	@Override
	public @NotNull String id() {
		return MovementChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		return ClientMovement.bind(player, operations, tick -> {
			ScheduledFuture<?> ticks = Clock.TICKS.scheduleAtFixedRate(tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
			return () -> ticks.cancel(false);
		});
	}

	/**
	 * Starts the thread of the physics ticks when the first player binds.
	 */
	private static final class Clock {
		private static final ScheduledExecutorService TICKS = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "anvil-client-physics");
			thread.setDaemon(true);
			return thread;
		});
	}
}
