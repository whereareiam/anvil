package me.whereareiam.anvil.capability.entities.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.KnownEntities;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPos;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundTeleportEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Installs the built-in entities capability for players that speak the Java Edition protocol. Each connection of the
 * player gets its own {@link EntityTracker}, numbered as the player's next connection, which applies the packets the
 * connection receives and measures the entities against the client's own player ({@code JavaProtocol.player()}),
 * which knows where the server placed the player and where it moved itself. The extension emits the facts the tracker
 * reports and answers
 * reads of the entities from the tracker of the current connection, so positions, which change with every move, never
 * travel as events. When a connection ends or is replaced, its entities disappear; when the player is destroyed,
 * nothing more is emitted.
 *
 * <p>Releases from Minecraft 1.21.2 on also correct positions through a sync packet, which the extension follows
 * where the player's release carries it.</p>
 */
public final class JavaEntitiesExtension implements WorkerExtension<Object> {
	/**
	 * The coordinates a move of an entity adds to where it is.
	 */
	private static final Set<Relative> DELTA = EnumSet.of(Relative.X, Relative.Y, Relative.Z);

	@Override
	public @NotNull String id() {
		return EntitiesChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations
	) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		Binding binding = new Binding(player, protocol);
		operations.register(EntitiesChannel.KNOWN, ignored -> binding.known());

		return binding.listen();
	}

	/**
	 * One player's trackers and listeners.
	 */
	@RequiredArgsConstructor
	private static final class Binding {
		private final PlayerBindingContext<Object> player;
		private final JavaProtocol protocol;
		private final AtomicInteger connections = new AtomicInteger();
		private final AtomicReference<EntityTracker> current = new AtomicReference<>();
		private final List<Subscription> listening = new ArrayList<>();

		private KnownEntities known() {
			return protocol.player().get(() -> {
				EntityTracker tracker = current.get();
				return new KnownEntities(tracker == null ? List.of() : tracker.known());
			});
		}

		private WorkerBinding listen() {
			Subscription connections = player.bindConnection(this::connected);
			facts(ClientboundLogin.class, (tracker, login) -> tracker.join(login.getEntityId().getValue()));
			facts(ClientboundRespawn.class, (tracker, respawn) -> tracker.clear());
			optionalFacts(ClientboundStartConfiguration.class, (tracker, configuration) -> tracker.clear());
			facts(ClientboundAddEntity.class, (tracker, add) -> tracker.spawn(add.getEntityId().getValue(),
					add.getUniqueId(), add.getType().toString(), add.getPosition(), add.getYaw(), add.getPitch()));
			moves();
			facts(ClientboundSetEntityData.class, (tracker, data) ->
					tracker.change(data.getEntityId().getValue(), data.getValues()));
			facts(ClientboundSetPassengers.class, (tracker, passengers) ->
					tracker.mount(passengers.getVehicleId().getValue(), values(passengers.getPassengerIds())));
			facts(ClientboundRemoveEntities.class, (tracker, removed) ->
					tracker.remove(values(removed.getEntityIds())));

			Subscription binding = Subscription.all(
					() -> current.set(null),
					Subscription.all(listening),
					connections
			);

			return binding::close;
		}

		/**
		 * Starts the tracker of a new connection, whose entities disappear when the connection ends unless the player
		 * was destroyed first.
		 */
		private Subscription connected() {
			EntityTracker tracker = new EntityTracker(connections.incrementAndGet(), new PlayerMeasure(protocol.player()));
			current.set(tracker);
			return () -> {
				protocol.player().run(() -> {
					if (current.compareAndSet(tracker, null)) emit(tracker.clear());
				});
			};
		}

		private void moves() {
			state(ClientboundMoveEntityPos.class, (tracker, move) ->
					tracker.move(move.getEntityId().getValue(), move.getDelta(), DELTA));
			state(ClientboundMoveEntityPosRot.class, (tracker, move) -> {
				tracker.move(move.getEntityId().getValue(), move.getDelta(), DELTA);
				tracker.turn(move.getEntityId().getValue(), move.getYaw(), move.getPitch(), Set.of());
			});
			state(ClientboundMoveEntityRot.class, (tracker, turn) ->
					tracker.turn(turn.getEntityId().getValue(), turn.getYaw(), turn.getPitch(), Set.of()));
			state(ClientboundTeleportEntity.class, (tracker, teleport) -> {
				tracker.move(teleport.getEntityId().getValue(), teleport.getPosition(), teleport.getRelatives());
				tracker.turn(teleport.getEntityId().getValue(), teleport.getYaw(), teleport.getPitch(),
						teleport.getRelatives());
			});
			if (protocol.supports(ClientboundEntityPositionSync.class))
				state(ClientboundEntityPositionSync.class, (tracker, sync) -> {
					tracker.move(sync.getEntityId().getValue(), sync.getPosition(), Set.of());
					tracker.turn(sync.getEntityId().getValue(), sync.getYaw(), sync.getPitch(), Set.of());
				});
		}

		/**
		 * Applies the packets of one type that cause facts, which the extension emits.
		 */
		private <P extends ClientboundPacket> void facts(
				Class<P> type,
				BiFunction<EntityTracker, P, List<EntityChange>> apply
		) {
			listening.add(protocol.listen(type, packet ->
					withTracker(tracker -> emit(apply.apply(tracker, packet)))));
		}

		private <P extends ClientboundPacket> void optionalFacts(
				Class<P> type,
				BiFunction<EntityTracker, P, List<EntityChange>> apply
		) {
			if (protocol.supports(type)) facts(type, apply);
		}

		/**
		 * Applies the packets of one type that only update the state reads see.
		 */
		private <P extends ClientboundPacket> void state(Class<P> type, BiConsumer<EntityTracker, P> apply) {
			listening.add(protocol.listen(type, packet -> withTracker(tracker -> apply.accept(tracker, packet))));
		}

		private void withTracker(Consumer<EntityTracker> action) {
			EntityTracker tracker = current.get();
			if (tracker != null) action.accept(tracker);
		}

		private void emit(List<EntityChange> changes) {
			for (EntityChange change : changes) player.emit(EntitiesChannel.CHANGED, change);
		}

		/**
		 * Reads the numbers of entity IDs, which the tracker and the channel carry.
		 */
		private static List<Integer> values(List<EntityId> entityIds) {
			return entityIds.stream().map(EntityId::getValue).toList();
		}
	}
}
