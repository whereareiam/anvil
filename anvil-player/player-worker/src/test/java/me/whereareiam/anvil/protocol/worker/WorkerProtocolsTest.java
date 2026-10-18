package me.whereareiam.anvil.protocol.worker;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerProtocolsTest {
	private final Greeter spoken = () -> "spoken";

	@Test
	void returnsTheProtocolItselfAndScopedViewsOfIt() {
		List<Consumer<Subscription>> tracks = new ArrayList<>();
		WorkerProtocols protocols = WorkerProtocols.builder("test")
				.speak(Greeter.class, spoken, track -> {
					tracks.add(track);
					return () -> "scoped";
				})
				.build();
		Consumer<Subscription> track = subscription -> { };

		assertSame(spoken, protocols.protocol(Greeter.class));
		assertEquals("scoped", protocols.protocol(Greeter.class, track).greeting());
		assertEquals(List.of(track), tracks);
	}

	@Test
	void refusesAProtocolThePlayerDoesNotSpeakNamingWhatItSpeaks() {
		WorkerProtocols protocols = WorkerProtocols.builder("test")
				.speak(Greeter.class, spoken, track -> spoken)
				.build();

		var refused = assertThrows(ProtocolUnavailableException.class, () -> protocols.protocol(Runnable.class));
		assertEquals("the test worker speaks " + Greeter.class.getName() + ", not java.lang.Runnable",
				refused.getMessage());
		var none = assertThrows(ProtocolUnavailableException.class,
				() -> WorkerProtocols.builder("test").build().protocol(Runnable.class));
		assertEquals("the test worker speaks no protocol, not java.lang.Runnable", none.getMessage());
	}

	@Test
	void refusesToSpeakAProtocolTwice() {
		var builder = WorkerProtocols.builder("test").speak(Greeter.class, spoken, track -> spoken);

		assertThrows(IllegalArgumentException.class, () -> builder.speak(Greeter.class, spoken, track -> spoken));
	}

	private interface Greeter {
		String greeting();
	}
}
