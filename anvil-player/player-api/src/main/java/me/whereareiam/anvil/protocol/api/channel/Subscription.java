package me.whereareiam.anvil.protocol.api.channel;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Owns a listener registration. Closing removes the listener and is idempotent.
 */
public interface Subscription extends AutoCloseable {
	/**
	 * Removes this registration without closing the event source.
	 */
	@Override
	void close();

	/**
	 * Returns one subscription owning several, such as every listener a worker binding registered. Closing it closes
	 * each of them in order, even when one fails, and then throws the first failure with the later ones suppressed.
	 *
	 * <pre>{@code
	 * Subscription listening = Subscription.all(
	 *         protocol.listen(ClientboundSystemChat.class, chat -> ...),
	 *         protocol.listen(ClientboundPlayerChat.class, chat -> ...)
	 * );
	 * return listening::close;
	 * }</pre>
	 *
	 * @param subscriptions subscriptions to close together, in the order to close them
	 * @return the composite subscription
	 */
	static @NotNull Subscription all(@NotNull Subscription... subscriptions) {
		return all(List.of(subscriptions));
	}

	/**
	 * Returns one subscription owning several, as {@link #all(Subscription...)} does, for subscriptions collected in a
	 * list. Later changes of the list do not change the subscriptions it owns.
	 *
	 * @param subscriptions subscriptions to close together, in the order to close them
	 * @return the composite subscription
	 */
	static @NotNull Subscription all(@NotNull List<? extends Subscription> subscriptions) {
		List<Subscription> owned = List.copyOf(subscriptions);
		return () -> {
			RuntimeException failure = null;
			for (Subscription subscription : owned) {
				try {
					subscription.close();
				} catch (RuntimeException closing) {
					if (failure == null) failure = closing;
					else failure.addSuppressed(closing);
				}
			}

			if (failure != null) throw failure;
		};
	}
}
