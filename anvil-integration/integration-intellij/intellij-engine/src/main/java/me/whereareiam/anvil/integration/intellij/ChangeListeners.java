package me.whereareiam.anvil.integration.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.NotNull;

/**
 * Change listeners whose registrations end with their owners, as used by the project services'
 * {@code subscribe(Runnable, Disposable)} contracts.
 */
public final class ChangeListeners {
	private final @NotNull List<Runnable> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Registers a listener until its owner is disposed.
	 */
	public void add(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener);
		Disposer.register(owner, () -> listeners.remove(listener));
	}

	/**
	 * Notifies every current listener on the calling thread, in registration order.
	 */
	public void notifyNow() {
		listeners.forEach(Runnable::run);
	}

	/**
	 * Notifies listeners later on the IDE event thread, in any modality, while the source is still active.
	 *
	 * @param active checked on the event thread; notification is skipped once it reports false
	 */
	public void notifyLater(@NotNull BooleanSupplier active) {
		ApplicationManager.getApplication().invokeLater(() -> {
			if (active.getAsBoolean()) notifyNow();
		}, ModalityState.any());
	}

	/**
	 * Drops every registration, for use when the source is disposed.
	 */
	public void clear() {
		listeners.clear();
	}
}
