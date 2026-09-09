package me.whereareiam.anvil.launcher.assembly.worker;

import lombok.Value;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolOperationRegistryTest {
	@Test
	void decodesDeclaredRequestModelsAndEncodesTypedResponses() {
		Map<String, Function<byte[], byte[]>> handlers = new HashMap<>();
		var registry = new ProtocolOperationRegistry(handlers::put);
		var operation = new ChannelOperation<>("example.echo", Message.class, Message.class);
		registry.register(operation, request -> {
			assertEquals(new Message("hello"), request);
			return new Message("reply:" + request.getText());
		});

		byte[] response = handlers.get(operation.getId()).apply(json("{\"text\":\"hello\"}"));
		assertEquals("{\"text\":\"reply:hello\"}", new String(response, StandardCharsets.UTF_8));
	}

	@Test
	void handlesVoidRequestsAndResponsesWithoutInventingModels() {
		Map<String, Function<byte[], byte[]>> handlers = new HashMap<>();
		var registry = new ProtocolOperationRegistry(handlers::put);
		AtomicInteger invoked = new AtomicInteger();
		registry.register(new ChannelOperation<>("example.ready", Void.class, String.class), request -> {
			assertNull(request);
			return "ready";
		});
		registry.register(new ChannelOperation<>("example.command", String.class, Void.class), request -> {
			assertEquals("command", request);
			invoked.incrementAndGet();
			return null;
		});

		assertEquals("\"ready\"", new String(handlers.get("example.ready").apply(json("null")), StandardCharsets.UTF_8));
		assertEquals("null", new String(handlers.get("example.command").apply(json("\"command\"")), StandardCharsets.UTF_8));
		assertEquals(1, invoked.get());
	}

	@Test
	void rejectsMalformedOrMissingRequiredPayloadsBeforeCallingTheHandler() {
		Map<String, Function<byte[], byte[]>> handlers = new HashMap<>();
		var registry = new ProtocolOperationRegistry(handlers::put);
		AtomicInteger invoked = new AtomicInteger();
		registry.register(new ChannelOperation<>("example.echo", Message.class, Message.class), request -> {
			invoked.incrementAndGet();
			return request;
		});

		for (String invalid : List.of("{", "null", "{}", "[]"))
			assertThrows(IllegalArgumentException.class, () -> handlers.get("example.echo").apply(json(invalid)));
		assertEquals(0, invoked.get());
	}

	@Test
	@SuppressWarnings({"rawtypes", "unchecked"})
	void rejectsResponsesOutsideTheRegisteredSchemaBeforeEncoding() {
		Map<String, Function<byte[], byte[]>> handlers = new HashMap<>();
		var registry = new ProtocolOperationRegistry(handlers::put);
		AtomicInteger invoked = new AtomicInteger();
		Function<Message, String> incompatible = (Function) (Function<Message, Integer>) request -> {
			invoked.incrementAndGet();
			return 42;
		};
		registry.register(new ChannelOperation<>("example.echo", Message.class, String.class), incompatible);

		assertThrows(ClassCastException.class, () -> handlers.get("example.echo").apply(json("{\"text\":\"hello\"}")));
		assertEquals(1, invoked.get());
	}

	@Test
	void propagatesNativeRegistrationFailuresWithoutReplacingThem() {
		IllegalStateException failure = new IllegalStateException("Native registration failed");
		NativeOperations nativeOperations = (id, handler) -> { throw failure; };
		var registry = new ProtocolOperationRegistry(nativeOperations);
		var operation = new ChannelOperation<>("example.echo", String.class, String.class);

		assertSame(failure, assertThrows(IllegalStateException.class, () -> registry.register(operation, Function.identity())));
	}

	private byte[] json(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	@Value
	private static class Message {
		String text;
	}
}
