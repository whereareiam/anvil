package me.whereareiam.anvil.capability.entities;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.entities.packet.EntitiesPackets;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Worker-side entities behavior over the packets of one library release: each native session the player connects
 * with gets its own {@link EntityTracker}, numbered as the player's next connection, which applies the session's
 * decoded packets. The binding emits the facts each tracker reports and answers reads of the entities from the
 * tracker of the latest session, so positions, which change with every move, never travel as events. The host
 * keeps only the changes of the latest connection, so a late packet of a replaced session cannot change what the
 * player sees now.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class EntitiesBinding<S> {
	private final @NotNull EntitiesPackets<S> packets;

	/**
	 * Registers the read of the known entities and observes every native session the player connects with.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that removes the native listener of the current session when the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		AtomicInteger connections = new AtomicInteger();
		AtomicReference<EntityTracker> current = new AtomicReference<>();
		operations.register(EntitiesOperations.KNOWN, ignored -> {
			EntityTracker tracker = current.get();
			return new KnownEntities(tracker == null ? List.of() : tracker.known());
		});

		Subscription listening = player.bindNativeSession(nativeSession -> {
			S session = packets.sessionType().cast(nativeSession);
			EntityTracker tracker = new EntityTracker(connections.incrementAndGet());
			current.set(tracker);
			Runnable detach = packets.listen(session,
					packet -> tracker.apply(packet).forEach(change -> player.emit(EntitiesOperations.CHANGED, change)));
			return detach::run;
		});
		return listening::close;
	}
}
