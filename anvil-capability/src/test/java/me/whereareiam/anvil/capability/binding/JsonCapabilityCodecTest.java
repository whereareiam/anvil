package me.whereareiam.anvil.capability.binding;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.protocol.api.model.player.PlayerConnectionEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JsonCapabilityCodecTest {
	private final JsonCapabilityCodec codec = new JsonCapabilityCodec();

	@Test
	void roundTripsImmutableCapabilitySchemasWithoutJsonTypesInTheirApis() {
		roundTrip(new MessageText("hello"));
		roundTrip(new ItemUse(Hand.MAIN));
		roundTrip(new BlockUse(1, 2, 3, BlockFace.UP, Hand.MAIN));
		roundTrip(new EntityUse(12, EntityInteraction.ATTACK, Hand.MAIN));
		roundTrip(new SlotSelection(1));
		roundTrip(new InventoryAction(2, InventoryClick.LEFT, 0));
		roundTrip(Position.builder().x(1).y(64).z(2).yaw(90).pitch(20).onGround(true).build());
		roundTrip(InventorySnapshot.builder().containerId(2).stateId(5)
				.item(InventoryItem.builder().slot(0).protocolId(1).amount(2).build()).build());
		roundTrip(new PlayerConnectionEvent(true, null));
		roundTrip(new PlayerConnectionEvent(false, "kicked"));
	}

	@Test
	void rejectsMissingOrMismatchedCapabilityPayloads() {
		assertThrows(IllegalArgumentException.class, () -> codec.decode(codec.encode(java.util.Map.of()), SlotSelection.class));
		assertThrows(IllegalArgumentException.class, () -> codec.decode(codec.encode(new MessageText("secret")), SlotSelection.class));
		assertThrows(IllegalArgumentException.class, () -> codec.decode("{\"slot\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8), SlotSelection.class));
	}

	@Test
	void encodedHandlersValidateRequestsBeforeInvokingTypedCode() {
		AtomicInteger invoked = new AtomicInteger();
		var operation = new ChannelOperation<>("test.slot", SlotSelection.class, MessageText.class);
		var handler = codec.handler(operation, request -> {
			invoked.incrementAndGet();
			return new MessageText("slot:" + request.getSlot());
		});

		assertEquals(new MessageText("slot:3"), codec.decode(handler.apply(codec.encode(new SlotSelection(3))), MessageText.class));
		assertThrows(IllegalArgumentException.class, () -> handler.apply(codec.encode(new MessageText("invalid"))));
		assertEquals(1, invoked.get());
	}

	@Test
	void encodedHandlersPreserveVoidPayloadsAndHandlerFailures() {
		var operation = new ChannelOperation<>("test.void", Void.class, Void.class);
		var handler = codec.handler(operation, request -> {
			assertNull(request);
			return null;
		});
		assertArrayEquals(codec.encode(null), handler.apply(codec.encode(null)));

		IllegalStateException failure = new IllegalStateException("handler failed");
		var failing = codec.handler(operation, request -> { throw failure; });
		assertSame(failure, assertThrows(IllegalStateException.class, () -> failing.apply(codec.encode(null))));
	}

	private <T> void roundTrip(T value) {
		assertEquals(value, codec.decode(codec.encode(value), value.getClass()));
	}

}
