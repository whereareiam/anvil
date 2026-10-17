package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Rebinds capability listeners across native session generations and owns their cleanup.
 */
final class NativeSessionBindings implements AutoCloseable {
	private final List<Registration> registrations = new ArrayList<>();
	private @Nullable Object current;
	private boolean closed;

	synchronized @NotNull Subscription register(@NotNull Function<Object, Subscription> listener) {
		if (closed) throw new IllegalStateException("Native session bindings are closed");

		Registration registration = new Registration(listener);
		if (current != null) registration.active = listener.apply(current);

		registrations.add(registration);
		return registration;
	}

	synchronized void attach(@NotNull Object session) {
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
