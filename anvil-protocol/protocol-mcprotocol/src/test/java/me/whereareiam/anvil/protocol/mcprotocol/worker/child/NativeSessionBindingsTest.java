package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import org.geysermc.mcprotocollib.network.ClientSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeSessionBindingsTest {
	@Test
	void reattachesOncePerGenerationAndDetachesOnUnsubscribe() {
		List<String> events = new ArrayList<>();
		ClientSession first = session();
		ClientSession second = session();
		try (var bindings = new NativeSessionBindings()) {
			var subscription = bindings.register(nativeSession -> {
				String generation = nativeSession == first ? "first" : "second";
				events.add("attach " + generation);
				return () -> events.add("close " + generation);
			});
			assertTrue(events.isEmpty());
			bindings.attach(first);
			bindings.attach(first);
			bindings.attach(second);
			subscription.close();
			subscription.close();
			assertEquals(List.of("attach first", "close first", "attach second", "close second"), events);
		}
		assertEquals(4, events.size());
	}

	@Test
	void closesAllRegistrationsDespiteOneCleanupFailure() {
		List<String> closed = new ArrayList<>();
		var bindings = new NativeSessionBindings();
		bindings.register(ignored -> () -> closed.add("first"));
		bindings.register(ignored -> () -> { closed.add("second"); throw new IllegalStateException("cleanup"); });
		bindings.attach(session());
		assertEquals("cleanup", assertThrows(IllegalStateException.class, bindings::close).getMessage());
		assertEquals(List.of("second", "first"), closed);
		bindings.close();
		assertThrows(IllegalStateException.class, () -> bindings.attach(session()));
	}

	@Test
	void attachmentErrorsRollbackEarlierRegistrationsAndPreserveCleanupErrors() {
		List<String> closed = new ArrayList<>();
		AssertionError failure = new AssertionError("attach failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		try (var bindings = new NativeSessionBindings()) {
			bindings.register(ignored -> () -> closed.add("first"));
			bindings.register(ignored -> () -> { closed.add("second"); throw cleanup; });
			bindings.register(ignored -> { throw failure; });
			assertSame(failure, assertThrows(AssertionError.class, () -> bindings.attach(session())));
			assertEquals(List.of("second", "first"), closed);
			assertArrayEquals(new Throwable[]{cleanup}, failure.getSuppressed());
		}
	}

	private ClientSession session() {
		return (ClientSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ClientSession.class},
				(proxy, method, args) -> { throw new AssertionError("Native session methods are outside listener ownership"); });
	}
}
