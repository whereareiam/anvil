package me.whereareiam.anvil.protocol.api.channel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionTest {
	@Test
	void closesEverySubscriptionInOrder() {
		List<String> closed = new ArrayList<>();

		Subscription.all(() -> closed.add("first"), () -> closed.add("second"), () -> closed.add("third")).close();

		assertEquals(List.of("first", "second", "third"), closed);
	}

	@Test
	void closesTheRestAfterAFailureAndThrowsTheFirstWithTheLaterOnesSuppressed() {
		List<String> closed = new ArrayList<>();
		IllegalStateException first = new IllegalStateException("first");
		IllegalArgumentException later = new IllegalArgumentException("later");
		Subscription subscription = Subscription.all(
				() -> {
					throw first;
				},
				() -> closed.add("second"),
				() -> {
					throw later;
				},
				() -> closed.add("fourth")
		);

		IllegalStateException failure = assertThrows(IllegalStateException.class, subscription::close);

		assertSame(first, failure);
		assertArrayEquals(new Throwable[]{later}, failure.getSuppressed());
		assertEquals(List.of("second", "fourth"), closed);
	}

	@Test
	void ownsTheSubscriptionsOfTheListWhenItWasCreated() {
		List<String> closed = new ArrayList<>();
		List<Subscription> subscriptions = new ArrayList<>(List.of(() -> closed.add("listed")));
		Subscription subscription = Subscription.all(subscriptions);

		subscriptions.add(() -> closed.add("added later"));
		subscription.close();

		assertEquals(List.of("listed"), closed);
	}
}
