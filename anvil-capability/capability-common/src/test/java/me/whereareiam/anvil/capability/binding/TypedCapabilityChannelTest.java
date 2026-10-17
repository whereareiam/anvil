package me.whereareiam.anvil.capability.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.MessageChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TypedCapabilityChannelTest {
	@Test
	void exchangesVoidRequestsAndResponsesAsJsonNull() {
		AtomicInteger dispatched = new AtomicInteger();
		RequestChannel channel = new TypedCapabilityChannel(new Messages((operation, request) -> {
			assertEquals("example.clear", operation);
			assertEquals("null", new String(request, StandardCharsets.UTF_8));
			dispatched.incrementAndGet();
			return "null".getBytes(StandardCharsets.UTF_8);
		}));

		assertNull(channel.request(new ChannelOperation<>("example.clear", Void.class, Void.class), null));
		assertEquals(1, dispatched.get());
	}

	@Test
	void encodesTheDeclaredRequestAndDecodesTheDeclaredResponse() {
		RequestChannel channel = new TypedCapabilityChannel(new Messages((operation, request) -> {
			assertEquals("example.length", operation);
			assertEquals("\"hello\"", new String(request, StandardCharsets.UTF_8));
			return "5".getBytes(StandardCharsets.UTF_8);
		}));

		assertEquals(5, channel.request(new ChannelOperation<>("example.length", String.class, Integer.class), "hello"));
	}

	@Test
	void rejectsAnAbsentRequiredRequestBeforeDispatching() {
		AtomicInteger dispatched = new AtomicInteger();
		RequestChannel channel = new TypedCapabilityChannel(new Messages((operation, request) -> {
			dispatched.incrementAndGet();
			return "null".getBytes(StandardCharsets.UTF_8);
		}));

		var failure = assertThrows(IllegalArgumentException.class,
				() -> channel.request(new ChannelOperation<>("example.required", String.class, Void.class), null));
		assertTrue(failure.getMessage().contains("example.required"));
		assertEquals(0, dispatched.get());
	}

	@RequiredArgsConstructor
	private static final class Messages implements MessageChannel {
		private final BiFunction<String, byte[], byte[]> exchange;

		@Override
		public byte @NotNull [] request(@NotNull String operation, byte @NotNull [] request) {
			return exchange.apply(operation, request);
		}

		@Override
		public @NotNull Subscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener) {
			throw new AssertionError("Requests must not subscribe to player events");
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			throw new AssertionError("Requests must not start player observation waits");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			throw new AssertionError("Requests must not query native capability bindings");
		}
	}
}
