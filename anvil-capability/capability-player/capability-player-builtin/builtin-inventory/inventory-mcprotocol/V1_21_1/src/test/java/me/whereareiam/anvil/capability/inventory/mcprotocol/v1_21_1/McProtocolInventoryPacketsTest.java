package me.whereareiam.anvil.capability.inventory.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolInventoryPacketsTest {
	private final McProtocolInventoryPackets packets = new McProtocolInventoryPackets();
	private final RecordingSession session = new RecordingSession();

	@Test
	void selectsTheHotbarSlot() {
		packets.selectHotbar(session.proxy, 3);

		assertEquals(3, assertInstanceOf(ServerboundSetCarriedItemPacket.class, session.single()).getSlot());
	}

	@Test
	void clicksWithTheContainerStateAndWithoutPredictedItems() {
		packets.click(session.proxy, click(InventoryClick.LEFT, 0));

		ServerboundContainerClickPacket packet = assertInstanceOf(ServerboundContainerClickPacket.class, session.single());
		assertEquals(2, packet.getContainerId());
		assertEquals(7, packet.getStateId());
		assertEquals(4, packet.getSlot());
		assertEquals(ContainerActionType.CLICK_ITEM, packet.getAction());
		assertEquals(ClickItemAction.LEFT_CLICK, packet.getParam());
		assertNull(packet.getCarriedItem());
		assertTrue(packet.getChangedSlots().isEmpty());
	}

	@Test
	void sendsEachClickModeAsItsContainerAction() {
		Map<InventoryClick, List<Object>> expected = Map.of(
				InventoryClick.LEFT, List.of(ContainerActionType.CLICK_ITEM, ClickItemAction.LEFT_CLICK),
				InventoryClick.RIGHT, List.of(ContainerActionType.CLICK_ITEM, ClickItemAction.RIGHT_CLICK),
				InventoryClick.SHIFT_LEFT, List.of(ContainerActionType.SHIFT_CLICK_ITEM, ShiftClickItemAction.LEFT_CLICK),
				InventoryClick.HOTBAR_SWAP, List.of(ContainerActionType.MOVE_TO_HOTBAR_SLOT, MoveToHotbarAction.SLOT_9),
				InventoryClick.DROP_ONE, List.of(ContainerActionType.DROP_ITEM, DropItemAction.DROP_FROM_SELECTED),
				InventoryClick.DROP_STACK, List.of(ContainerActionType.DROP_ITEM, DropItemAction.DROP_SELECTED_STACK)
		);

		for (InventoryClick click : InventoryClick.values()) {
			packets.click(session.proxy, click(click, click == InventoryClick.HOTBAR_SWAP ? 8 : 0));
			ServerboundContainerClickPacket packet = (ServerboundContainerClickPacket) session.sent.getLast();
			assertEquals(expected.get(click), List.<Object>of(packet.getAction(), packet.getParam()), click.name());
		}
	}

	@Test
	void sendsTheOffHandSwapButtonAsTheOffHandAction() {
		packets.click(session.proxy, click(InventoryClick.HOTBAR_SWAP, ContainerClick.OFF_HAND_BUTTON));

		ServerboundContainerClickPacket packet = (ServerboundContainerClickPacket) session.sent.getLast();
		assertEquals(MoveToHotbarAction.OFF_HAND, packet.getParam());
	}

	@Test
	void reportsTheInventoryPacketsTheSessionReceives() {
		RecordingListener listener = new RecordingListener();
		packets.listen(session.proxy, listener);

		session.receive(new ClientboundOpenScreenPacket(2, ContainerType.GENERIC_9X1, Component.text("Chest")));
		session.receive(new ClientboundContainerSetContentPacket(2, 7, new ItemStack[]{null, new ItemStack(800, 3)}, null));
		session.receive(new ClientboundContainerSetSlotPacket(2, 8, 0, new ItemStack(1, 64)));
		// The library decodes this container ID as an unsigned byte, so vanilla's cursor -1 and inventory index -2 arrive as
		// 255 and 254.
		session.receive(new ClientboundContainerSetSlotPacket(255, 8, -1, null));
		session.receive(new ClientboundContainerSetSlotPacket(254, 0, 40, new ItemStack(5, 1)));
		session.receive(new ServerboundSetCarriedItemPacket(1));

		assertEquals(List.of(
				List.of("opened", 2),
				List.of("contents", 2, 7, List.of(item(1, 800, 3))),
				List.of("slot", 2, 8, 0, item(0, 1, 64)),
				Arrays.asList("slot", -1, 8, -1, null),
				List.of("playerSlot", 40, item(40, 5, 1))
		), listener.events);
		assertTrue(session.sent.isEmpty());
	}

	@Test
	void stopsReportingOnceDisconnectedAndDetaches() {
		RecordingListener listener = new RecordingListener();
		Runnable detach = packets.listen(session.proxy, listener);

		session.connected = false;
		session.receive(new ClientboundOpenScreenPacket(2, ContainerType.GENERIC_9X1, Component.text("Chest")));
		detach.run();

		assertTrue(listener.events.isEmpty());
		assertTrue(session.listeners.isEmpty());
	}

	@Test
	void isTheOnlyInventoryPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(InventoryPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolInventoryPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	private static ContainerClick click(InventoryClick click, int button) {
		return ContainerClick.builder()
				.containerId(2)
				.stateId(7)
				.actionId(3)
				.slot(4)
				.click(click)
				.button(button)
				.clickedItem(item(4, 800, 3))
				.build();
	}

	private static InventoryItem item(int slot, int protocolId, int amount) {
		return InventoryItem.builder().slot(slot).protocolId(protocolId).amount(amount).build();
	}

	/**
	 * Records each listener call with its arguments.
	 */
	private static final class RecordingListener implements InventoryPacketListener {
		private final List<List<Object>> events = new ArrayList<>();

		@Override
		public void opened(int containerId) {
			events.add(List.of("opened", containerId));
		}

		@Override
		public void contents(int containerId, int stateId, @NotNull List<InventoryItem> items) {
			events.add(List.of("contents", containerId, stateId, items));
		}

		@Override
		public void slot(int containerId, int stateId, int slot, @Nullable InventoryItem item) {
			events.add(Arrays.asList("slot", containerId, stateId, slot, item));
		}

		@Override
		public void playerSlot(int slot, @Nullable InventoryItem item) {
			events.add(Arrays.asList("playerSlot", slot, item));
		}
	}

	/**
	 * A release session proxy that records sent packets and listeners and delivers received packets to them.
	 */
	private static final class RecordingSession implements InvocationHandler {
		private final List<Object> sent = new ArrayList<>();
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class}, this);
		private boolean connected = true;

		@Override
		public Object invoke(Object proxy, Method method, Object[] arguments) {
			switch (method.getName()) {
				case "send" -> sent.add(arguments[0]);
				case "addListener" -> listeners.add((SessionListener) arguments[0]);
				case "removeListener" -> listeners.remove(arguments[0]);
				case "isConnected" -> {
					return connected;
				}
				default -> throw new UnsupportedOperationException(method.getName());
			}

			return null;
		}

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private Object single() {
			assertEquals(1, sent.size(), sent.toString());
			return sent.getFirst();
		}
	}
}
