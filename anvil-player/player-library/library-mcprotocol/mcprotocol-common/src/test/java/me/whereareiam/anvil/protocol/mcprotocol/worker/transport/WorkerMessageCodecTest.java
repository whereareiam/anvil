package me.whereareiam.anvil.protocol.mcprotocol.worker.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
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
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerReady;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class WorkerMessageCodecTest {
	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	@Test
	void roundTripsEveryEnvelopeWithoutTreatingDiagnosticsAsMessages() {
		var result = JsonNodeFactory.instance.objectNode().put("message", "line\nbreak");
		var response = WorkerResponse.builder().id(1).success(true).result(result).build();
		var event = WorkerEvent.builder().event("example.changed").player("player").payload(result).build();
		var ready = WorkerReady.builder().release("1.21.11").protocol(774).segment("mcprotocol-java", "1.21.11")
				.capabilities(Set.of("example.capability"))
				.unavailableCapability("example.missing", "NoClassDefFoundError: example/Missing").build();
		assertEquals(response, codec.decodeMessage(codec.encodeMessage(response)).orElseThrow());
		assertEquals(event, codec.decodeMessage(codec.encodeMessage(event)).orElseThrow());
		assertEquals(ready, codec.decodeMessage(codec.encodeMessage(ready)).orElseThrow());
		assertTrue(codec.decodeMessage("ordinary worker diagnostic").isEmpty());
	}

	@Test
	void carriesCredentialsPrivatelyWithoutIncludingThemInModelDiagnostics() {
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(UUID.randomUUID()).build())
				.credentials(WorkerCredentials.builder().accessToken("private-token").build())
				.connection(WorkerConnection.builder().host("localhost").port(25565)
						.virtualHost("lobby.example.test").sourceAddress("127.0.0.2").build())
				.build();
		var request = WorkerRequest.builder().id(1).operation(WorkerControlOperation.CREATE_PLAYER.getWireName())
				.player("player").arguments(codec.payload(options)).build();
		var decoded = codec.decodeRequest(codec.encodeRequest(request));
		assertEquals(options, codec.decodePayload(decoded.getArguments(), WorkerPlayerOptions.class));
		assertFalse(options.toString().contains("private-token"));
		assertFalse(request.toString().contains("private-token"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"ANVIL:null", "ANVIL:[]", "ANVIL:{}", "ANVIL:{\"id\":1}",
			"ANVIL:{\"event\":\"ready\",\"protocol\":774}", "ANVIL:{\"event\":\"example.event\"}",
			"ANVIL:{\"event\":\"ready\",\"protocol\":774,\"capabilities\":[],\"unavailable\":{}}",
			"ANVIL:{\"event\":\"ready\",\"release\":\"1.21.11\",\"protocol\":774,\"capabilities\":[]}",
			"ANVIL:{\"event\":\"ready\",\"release\":\"1.21.11\",\"protocol\":774,\"capabilities\":[],\"unavailable\":{}}"
	})
	void rejectsMalformedWorkerEnvelopes(String line) {
		assertThrows(IllegalArgumentException.class, () -> codec.decodeMessage(line));
	}

	@Test
	void neverIncludesMalformedPayloadsOrParserCausesInFailures() {
		var failure = assertThrows(IllegalArgumentException.class, () -> codec.decodeRequest(
				"{\"id\":1,\"operation\":\"create\",\"arguments\":{\"accessToken\":\"private-token\"},BROKEN}"
		));
		assertEquals("Invalid worker message", failure.getMessage());
		assertNull(failure.getCause());
		assertThrows(IllegalArgumentException.class, () -> codec.decodeRequest("{}"));
	}

	@Test
	void roundTripsImmutablePlayerValuesInTheirEnvelope() {
		roundTrip(new MessageText("hello"));
		roundTrip("text");
		roundTrip(5);
		roundTrip(new ItemUse(Hand.MAIN));
		roundTrip(new BlockUse(1, 2, 3, BlockFace.UP, Hand.MAIN));
		roundTrip(new EntityUse(12, EntityInteraction.ATTACK, Hand.MAIN));
		roundTrip(new SlotSelection(1));
		roundTrip(new InventoryAction(2, InventoryClick.LEFT, 0));
		roundTrip(Position.builder().x(1).y(64).z(2).yaw(90).pitch(20).onGround(true).build());
		roundTrip(InventorySnapshot.builder()
				.containerId(2)
				.stateId(5)
				.item(InventoryItem.builder().slot(0).protocolId(1).amount(2).build())
				.build());
		roundTrip(new PlayerConnectionEvent(true, null, null, new PlayerProfile("Steve_Premium", UUID.randomUUID())));
		roundTrip(new PlayerConnectionEvent(false, "kicked", DisconnectCause.SERVER, null));
	}

	@Test
	void carriesVoidAsAnEnvelopeHoldingNull() {
		ObjectNode envelope = codec.envelope(null);

		assertTrue(envelope.path("value").isNull());
		assertNull(codec.open(envelope, Void.class));
		assertNull(codec.open(JsonNodeFactory.instance.objectNode(), Void.class));
	}

	@Test
	void rejectsMissingNullOrMismatchedValuesWithoutRevealingThem() {
		ObjectNode missing = JsonNodeFactory.instance.objectNode();
		ObjectNode nullPrimitive = JsonNodeFactory.instance.objectNode();
		nullPrimitive.putObject("value").putNull("slot");
		ObjectNode notAnObject = JsonNodeFactory.instance.objectNode();
		notAnObject.putArray("value");

		for (JsonNode invalid : List.of(missing, codec.envelope(null), codec.envelope(Map.of()), nullPrimitive, notAnObject))
			assertThrows(IllegalArgumentException.class, () -> codec.open(invalid, SlotSelection.class), invalid.toString());

		var failure = assertThrows(IllegalArgumentException.class, () -> codec.open(codec.envelope(new MessageText("secret")), SlotSelection.class));
		assertEquals("Invalid player message for " + SlotSelection.class.getName(), failure.getMessage());
		assertNull(failure.getCause());
	}

	@Test
	void handlersOpenTheDeclaredRequestBeforeInvokingTypedCode() {
		AtomicInteger invoked = new AtomicInteger();
		var operation = new OperationDescriptor<>("test.slot", SlotSelection.class, MessageText.class);
		var handler = codec.handler(operation, request -> {
			invoked.incrementAndGet();
			return new MessageText("slot:" + request.getSlot());
		});

		assertEquals(new MessageText("slot:3"), codec.open(handler.apply(codec.envelope(new SlotSelection(3))), MessageText.class));
		assertThrows(IllegalArgumentException.class, () -> handler.apply(codec.envelope(new MessageText("invalid"))));
		assertThrows(IllegalArgumentException.class, () -> handler.apply(codec.envelope(null)));
		assertEquals(1, invoked.get());
	}

	@Test
	void handlersPassVoidRequestsAndResponsesAndPreserveHandlerFailures() {
		var operation = new OperationDescriptor<>("test.void", Void.class, Void.class);
		var handler = codec.handler(operation, request -> {
			assertNull(request);
			return null;
		});
		assertEquals(codec.envelope(null), handler.apply(codec.envelope(null)));

		IllegalStateException failure = new IllegalStateException("handler failed");
		var failing = codec.handler(operation, request -> { throw failure; });
		assertSame(failure, assertThrows(IllegalStateException.class, () -> failing.apply(codec.envelope(null))));
	}

	@Test
	@SuppressWarnings({"rawtypes", "unchecked"})
	void handlersRejectResponsesOutsideTheDeclaredResponseType() {
		AtomicInteger invoked = new AtomicInteger();
		Function<MessageText, String> incompatible = (Function) (Function<MessageText, Integer>) request -> {
			invoked.incrementAndGet();
			return 42;
		};
		var handler = codec.handler(new OperationDescriptor<>("test.echo", MessageText.class, String.class), incompatible);

		assertThrows(ClassCastException.class, () -> handler.apply(codec.envelope(new MessageText("hello"))));
		assertEquals(1, invoked.get());
	}

	private void roundTrip(Object value) {
		assertEquals(value, codec.open(codec.envelope(value), value.getClass()));
	}
}
