package me.whereareiam.anvil.protocol.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Rebinds one player's connection listeners across its native session generations and owns their cleanup: a
 * generation's listeners are released when its connection ends or a new session replaces it. A library's player
 * implements {@code PlayerBindingContext.bindNativeSession} with {@link #register(Function)}, attaches each session
 * it opens before connecting it, and ends the session when its connection ends.
 *
 * <pre>{@code
 * Object session = client.open(login, listener);
 * bindings.attach(session);
 * client.connect(session);
 * ...
 * bindings.end(session); // when the connection ended
 * }</pre>
 */
public final class NativeSessionBindings implements AutoCloseable {
	private final List<Registration> registrations = new ArrayList<>();
	private @Nullable Object current;
	private boolean closed;

	/**
	 * Registers a listener that runs for the current session, if any, and for every session attached later; the
	 * subscription it returns for a session closes when that session's generation ends.
	 *
	 * @param listener binds the listener to one native session
	 * @return subscription removing the listener and closing its current binding
	 * @throws IllegalStateException when the bindings are closed
	 */
	public synchronized @NotNull Subscription register(@NotNull Function<Object, Subscription> listener) {
		if (closed) throw new IllegalStateException("Native session bindings are closed");

		Registration registration = new Registration(listener);
		if (current != null) registration.active = listener.apply(current);

		registrations.add(registration);
		return registration;
	}

	/**
	 * Makes a session the current generation: the previous generation's bindings close and every listener binds to
	 * the session. Attaching the current session again changes nothing. When a listener fails, the bindings made for
	 * the session are closed and the failure is rethrown.
	 *
	 * @param session the native session the player opened
	 * @throws IllegalStateException when the bindings are closed
	 */
	public synchronized void attach(@NotNull Object session) {
		if (closed) throw new IllegalStateException("Native session bindings are closed");
		if (session == current) return;

		current = null;
		detach();
		current = session;

		try {
			for (Registration registration : List.copyOf(registrations))
				registration.active = registration.listener.apply(session);
		} catch (RuntimeException | Error failure) {
			current = null;
			try {
				detach();
			} catch (RuntimeException | Error cleanup) {
				if (failure != cleanup) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	/**
	 * Ends the generation of a session whose connection ended; the next session that attaches binds the listeners
	 * again. A session that is no longer current changes nothing.
	 *
	 * @param session the session whose connection ended
	 */
	public synchronized void end(@NotNull Object session) {
		if (closed || session != current) return;

		current = null;
		detach();
	}

	/**
	 * Closes every current binding and refuses later registrations and sessions.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		current = null;

		try {
			detach();
		} finally {
			registrations.clear();
		}
	}

	private void detach() {
		Throwable failure = null;
		for (Registration registration : List.copyOf(registrations).reversed()) {
			try {
				registration.detach();
			} catch (RuntimeException | Error cleanup) {
				if (failure == null) failure = cleanup;
				else if (cleanup != failure) failure.addSuppressed(cleanup);
			}
		}

		if (failure instanceof RuntimeException exception) throw exception;
		if (failure instanceof Error error) throw error;
	}

	@RequiredArgsConstructor
	private final class Registration implements Subscription {
		private final Function<Object, Subscription> listener;
		private @Nullable Subscription active;

		private void detach() {
			Subscription previous = active;
			active = null;
			if (previous != null) previous.close();
		}

		@Override
		public void close() {
			synchronized (NativeSessionBindings.this) {
				registrations.remove(this);
				detach();
			}
		}
	}
}
