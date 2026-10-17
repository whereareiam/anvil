package me.whereareiam.anvil.capability.entities.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.entities.model.EntityItem;
import me.whereareiam.anvil.capability.entities.packet.EntitiesPackets;
import me.whereareiam.anvil.capability.entities.packet.model.EntitiesRemoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityDataChanged;
import me.whereareiam.anvil.capability.entities.packet.model.EntityMoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityPacket;
import me.whereareiam.anvil.capability.entities.packet.model.EntitySpawned;
import me.whereareiam.anvil.capability.entities.packet.model.EntityTurned;
import me.whereareiam.anvil.capability.entities.packet.model.PassengersChanged;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerJoined;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerMoved;
import me.whereareiam.anvil.capability.entities.packet.model.WorldCleared;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomName;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomNameVisible;
import me.whereareiam.anvil.capability.entities.packet.model.data.DisplayText;
import me.whereareiam.anvil.capability.entities.packet.model.data.SharedFlags;
import me.whereareiam.anvil.capability.entities.packet.model.data.ShownItem;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McProtocolEntitiesPacketsTest {
	private static final UUID STAND = new UUID(0, 7);

	private final McProtocolEntitiesPackets packets = new McProtocolEntitiesPackets();

	@Test
	void reportsSpawnsWithTheNamespacedType() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundAddEntityPacket(7, STAND, EntityType.ARMOR_STAND, 1.5, 64, -2.5, 90, 10, 45));
		session.receive(new ClientboundAddEntityPacket(8, new UUID(0, 8), EntityType.TEXT_DISPLAY, 0, 66, 0, 0, 0, 0));

		assertEquals(List.of(
				EntitySpawned.builder().entityId(7).uniqueId(STAND).type("minecraft:armor_stand").x(1.5).y(64).z(-2.5)
						.yaw(90).pitch(10).build(),
				EntitySpawned.builder().entityId(8).uniqueId(new UUID(0, 8)).type("minecraft:text_display").y(66).build()
		), session.decoded);
	}

	@Test
	void decodesTheSharedFlagsTheCustomNameTheDisplayTextAndTheShownItem() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundSetEntityDataPacket(7, new EntityMetadata<?, ?>[]{
				new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0x63),
				new ObjectEntityMetadata<>(2, MetadataTypes.OPTIONAL_COMPONENT,
						Optional.of(Component.text("Alice: ").append(Component.text("hello")))),
				new BooleanEntityMetadata(3, MetadataTypes.BOOLEAN, true),
				new BooleanEntityMetadata(5, MetadataTypes.BOOLEAN, true),
				new ObjectEntityMetadata<>(23, MetadataTypes.COMPONENT, Component.text("bubble"))
		}));
		session.receive(new ClientboundSetEntityDataPacket(8, new EntityMetadata<?, ?>[]{
				new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0x9C),
				new ObjectEntityMetadata<>(2, MetadataTypes.OPTIONAL_COMPONENT, Optional.empty()),
				new BooleanEntityMetadata(3, MetadataTypes.BOOLEAN, false),
				new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, new ItemStack(871, 3))
		}));
		session.receive(new ClientboundSetEntityDataPacket(9, new EntityMetadata<?, ?>[]{
				new ObjectEntityMetadata<>(23, MetadataTypes.ITEM_STACK, new ItemStack(40, 1)),
				new ObjectEntityMetadata<>(8, MetadataTypes.ITEM_STACK, null)
		}));

		assertEquals(List.of(
				new EntityDataChanged(7, List.of(SharedFlags.builder().onFire(true).sneaking(true).invisible(true).glowing(true).build(),
						new CustomName("Alice: hello"), new CustomNameVisible(true), new DisplayText("bubble"))),
				new EntityDataChanged(8, List.of(SharedFlags.builder().sprinting(true).swimming(true).gliding(true).build(),
						new CustomName(null), new CustomNameVisible(false), new ShownItem(new EntityItem(871, 3)))),
				new EntityDataChanged(9, List.of(new ShownItem(new EntityItem(40, 1)), new ShownItem(null)))
		), session.decoded);
	}

	@Test
	void reportsNoDataChangeWithoutAValueItDecodes() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundSetEntityDataPacket(7, new EntityMetadata<?, ?>[]{
				new BooleanEntityMetadata(5, MetadataTypes.BOOLEAN, true),
				new ObjectEntityMetadata<>(15, MetadataTypes.COMPONENT, Component.text("last output"))
		}));

		assertEquals(List.of(), session.decoded);
	}

	@Test
	void reportsMovesTurnsTeleportsAndPositionSyncs() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundMoveEntityPosPacket(7, 0.5, -1, 0.25, true));
		session.receive(new ClientboundMoveEntityPosRotPacket(7, 1, 0, 0, 90, 10, true));
		session.receive(new ClientboundMoveEntityRotPacket(7, 180, -10, true));
		session.receive(new ClientboundTeleportEntityPacket(7, Vector3d.from(1, 2, 3), Vector3d.ZERO, 15, 5,
				List.of(PositionElement.Y, PositionElement.Y_ROT), true));
		session.receive(new ClientboundEntityPositionSyncPacket(7, Vector3d.from(4, 5, 6), Vector3d.ZERO, 30, 20, true));

		assertEquals(List.of(
				EntityMoved.builder().entityId(7).x(0.5).y(-1).z(0.25).relativeX(true).relativeY(true).relativeZ(true).build(),
				EntityMoved.builder().entityId(7).x(1).relativeX(true).relativeY(true).relativeZ(true).build(),
				EntityTurned.builder().entityId(7).yaw(90).pitch(10).build(),
				EntityTurned.builder().entityId(7).yaw(180).pitch(-10).build(),
				EntityMoved.builder().entityId(7).x(1).y(2).z(3).relativeY(true).build(),
				EntityTurned.builder().entityId(7).yaw(15).pitch(5).relativeYaw(true).build(),
				EntityMoved.builder().entityId(7).x(4).y(5).z(6).build(),
				EntityTurned.builder().entityId(7).yaw(30).pitch(20).build()
		), session.decoded);
	}

	@Test
	void reportsPassengersAndRemovals() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundSetPassengersPacket(42, new int[]{7, 8}));
		session.receive(new ClientboundRemoveEntitiesPacket(new int[]{7}));

		assertEquals(List.of(new PassengersChanged(42, List.of(7, 8)), new EntitiesRemoved(List.of(7))), session.decoded);
	}

	@Test
	void reportsThePlayersEntityItsPositionAndEveryResetOfTheWorld() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10, false, true, false, null, false));
		session.receive(new ClientboundPlayerPositionPacket(1, Vector3d.from(10, 64, 10), Vector3d.ZERO, 0, 0,
				List.of(PositionElement.X)));
		session.send(new ServerboundMovePlayerPosPacket(true, false, 11, 64, 12));
		session.receive(new ClientboundRespawnPacket(null, false, false));
		session.receive(new ClientboundStartConfigurationPacket());
		session.receive(new ClientboundKeepAlivePacket(7));
		session.disconnect();

		assertEquals(List.of(
				new PlayerJoined(42),
				PlayerMoved.builder().x(10).y(64).z(10).relativeX(true).build(),
				PlayerMoved.builder().x(11).y(64).z(12).build(),
				new WorldCleared(),
				new WorldCleared(),
				new WorldCleared()
		), session.decoded);
	}

	@Test
	void removesItsListenerWhenDetached() {
		NativeSession session = new NativeSession();
		assertEquals(1, session.listeners.size());

		session.detach.run();
		session.receive(new ClientboundRemoveEntitiesPacket(new int[]{7}));

		assertEquals(List.of(), session.listeners);
		assertEquals(List.of(), session.decoded);
	}

	@Test
	void isTheOnlyEntitiesPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(EntitiesPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolEntitiesPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	/**
	 * An MCProtocolLib session the port listens to, which delivers received and sent packets synchronously and
	 * records what the port decodes.
	 */
	private final class NativeSession {
		private final List<SessionListener> listeners = new ArrayList<>();
		private final List<EntityPacket> decoded = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));
		private final Runnable detach = packets.listen(proxy, decoded::add);

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private void send(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetSent(proxy, packet));
		}

		private void disconnect() {
			List.copyOf(listeners).forEach(listener -> listener.disconnected(new DisconnectedEvent(proxy, Component.text("bye"), null)));
		}

		private Object handle(Method method, Object[] arguments) {
			switch (method.getName()) {
				case "addListener" -> listeners.add((SessionListener) arguments[0]);
				case "removeListener" -> listeners.remove((SessionListener) arguments[0]);
				default -> throw new UnsupportedOperationException(method.getName());
			}
			return null;
		}
	}
}
