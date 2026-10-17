package me.whereareiam.anvil.capability.entities.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.data.game.entity.type.PaintingType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddMobPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
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
import me.whereareiam.anvil.capability.entities.packet.model.data.SharedFlags;
import me.whereareiam.anvil.capability.entities.packet.model.data.ShownItem;
import net.kyori.adventure.text.Component;
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

		session.receive(new ClientboundAddEntityPacket(7, STAND, EntityType.ARMOR_STAND, 1.5, 64, -2.5, 90, 10));
		session.receive(new ClientboundAddMobPacket(8, new UUID(0, 8), EntityType.ZOMBIE, 0, 66, 0, 20, 180, 45, 0, 0, 0));
		session.receive(new ClientboundAddPlayerPacket(9, new UUID(0, 9), 2, 64, 2, 270, -30));
		session.receive(new ClientboundAddPaintingPacket(10, new UUID(0, 10), PaintingType.KEBAB, new Position(4, 65, 4), Direction.NORTH));
		session.receive(new ClientboundAddExperienceOrbPacket(11, 1, 2, 3, 5));

		assertEquals(List.of(
				EntitySpawned.builder().entityId(7).uniqueId(STAND).type("minecraft:armor_stand").x(1.5).y(64).z(-2.5)
						.yaw(90).pitch(10).build(),
				EntitySpawned.builder().entityId(8).uniqueId(new UUID(0, 8)).type("minecraft:zombie").y(66).yaw(180).pitch(20).build(),
				EntitySpawned.builder().entityId(9).uniqueId(new UUID(0, 9)).type("minecraft:player").x(2).y(64).z(2)
						.yaw(270).pitch(-30).build(),
				EntitySpawned.builder().entityId(10).uniqueId(new UUID(0, 10)).type("minecraft:painting").x(4).y(65).z(4).build(),
				EntitySpawned.builder().entityId(11).type("minecraft:experience_orb").x(1).y(2).z(3).build()
		), session.decoded);
	}

	@Test
	void decodesTheSharedFlagsTheCustomNameAndTheShownItem() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundSetEntityDataPacket(7, new EntityMetadata<?, ?>[]{
				new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0x63),
				new ObjectEntityMetadata<>(2, MetadataType.OPTIONAL_CHAT,
						Optional.of(Component.text("Alice: ").append(Component.text("hello")))),
				new BooleanEntityMetadata(3, MetadataType.BOOLEAN, true),
				new BooleanEntityMetadata(5, MetadataType.BOOLEAN, true),
				new ObjectEntityMetadata<>(23, MetadataType.CHAT, Component.text("not a display"))
		}));
		session.receive(new ClientboundSetEntityDataPacket(8, new EntityMetadata<?, ?>[]{
				new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0x9C),
				new ObjectEntityMetadata<>(2, MetadataType.OPTIONAL_CHAT, Optional.empty()),
				new BooleanEntityMetadata(3, MetadataType.BOOLEAN, false),
				new ObjectEntityMetadata<>(8, MetadataType.ITEM, new ItemStack(871, 3))
		}));
		session.receive(new ClientboundSetEntityDataPacket(9, new EntityMetadata<?, ?>[]{
				new ObjectEntityMetadata<>(23, MetadataType.ITEM, new ItemStack(40, 1)),
				new ObjectEntityMetadata<>(8, MetadataType.ITEM, null)
		}));

		assertEquals(List.of(
				new EntityDataChanged(7, List.of(SharedFlags.builder().onFire(true).sneaking(true).invisible(true).glowing(true).build(),
						new CustomName("Alice: hello"), new CustomNameVisible(true))),
				new EntityDataChanged(8, List.of(SharedFlags.builder().sprinting(true).swimming(true).gliding(true).build(),
						new CustomName(null), new CustomNameVisible(false), new ShownItem(new EntityItem(871, 3)))),
				new EntityDataChanged(9, List.of(new ShownItem(null)))
		), session.decoded);
	}

	@Test
	void reportsNoDataChangeWithoutAValueItDecodes() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundSetEntityDataPacket(7, new EntityMetadata<?, ?>[]{
				new BooleanEntityMetadata(5, MetadataType.BOOLEAN, true),
				new ObjectEntityMetadata<>(15, MetadataType.CHAT, Component.text("last output"))
		}));

		assertEquals(List.of(), session.decoded);
	}

	@Test
	void reportsMovesTurnsAndAbsoluteTeleports() {
		NativeSession session = new NativeSession();

		session.receive(new ClientboundMoveEntityPosPacket(7, 0.5, -1, 0.25, true));
		session.receive(new ClientboundMoveEntityPosRotPacket(7, 1, 0, 0, 90, 10, true));
		session.receive(new ClientboundMoveEntityRotPacket(7, 180, -10, true));
		session.receive(new ClientboundTeleportEntityPacket(7, 1, 2, 3, 15, 5, true));

		assertEquals(List.of(
				EntityMoved.builder().entityId(7).x(0.5).y(-1).z(0.25).relativeX(true).relativeY(true).relativeZ(true).build(),
				EntityMoved.builder().entityId(7).x(1).relativeX(true).relativeY(true).relativeZ(true).build(),
				EntityTurned.builder().entityId(7).yaw(90).pitch(10).build(),
				EntityTurned.builder().entityId(7).yaw(180).pitch(-10).build(),
				EntityMoved.builder().entityId(7).x(1).y(2).z(3).build(),
				EntityTurned.builder().entityId(7).yaw(15).pitch(5).build()
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

		session.receive(new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, 1, new String[0], new CompoundTag(""), new CompoundTag(""), "minecraft:overworld", 0, 20, 10,
				10, false, true, false, false));
		session.receive(new ClientboundPlayerPositionPacket(10, 64, 10, 0, 0, 1, false, PositionElement.X));
		session.send(new ServerboundMovePlayerPosPacket(true, 11, 64, 12));
		session.receive(new ClientboundRespawnPacket(new CompoundTag(""), "minecraft:overworld", 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, false));
		session.receive(new ClientboundKeepAlivePacket(7));
		session.disconnect();

		assertEquals(List.of(
				new PlayerJoined(42),
				PlayerMoved.builder().x(10).y(64).z(10).relativeX(true).build(),
				PlayerMoved.builder().x(11).y(64).z(12).build(),
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
			List.copyOf(listeners).forEach(listener -> listener.disconnected(new DisconnectedEvent(proxy, "bye")));
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
