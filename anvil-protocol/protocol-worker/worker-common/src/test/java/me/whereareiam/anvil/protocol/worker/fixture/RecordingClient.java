package me.whereareiam.anvil.protocol.worker.fixture;

import me.whereareiam.anvil.protocol.worker.WorkerClient;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerOptions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory client whose sessions are plain objects; tests drive the listener callbacks a release would send.
 */
public final class RecordingClient implements WorkerClient {
	private final List<WorkerPlayerOptions> opened = new ArrayList<>();
	private final Map<Object, SessionListener> listeners = new LinkedHashMap<>();
	private final Set<Object> connected = new LinkedHashSet<>();
	private final List<String> disconnects = new ArrayList<>();

	@Override
	public int protocolNumber() {
		return 774;
	}

	@Override
	public @NotNull Class<?> sessionType() {
		return Object.class;
	}

	@Override
	public synchronized @NotNull Object open(@NotNull WorkerPlayerOptions options, @NotNull SessionListener listener) {
		Object session = new Object();
		opened.add(options);
		listeners.put(session, listener);
		return session;
	}

	@Override
	public synchronized void connect(@NotNull Object session) {
		connected.add(session);
	}

	@Override
	public synchronized boolean connected(@NotNull Object session) {
		return connected.contains(session);
	}

	@Override
	public synchronized void disconnect(@NotNull Object session, @NotNull String reason) {
		connected.remove(session);
		disconnects.add(reason);
	}

	public synchronized @NotNull List<WorkerPlayerOptions> opened() {
		return List.copyOf(opened);
	}

	public synchronized @NotNull List<Object> sessions() {
		return List.copyOf(listeners.keySet());
	}

	public synchronized @NotNull SessionListener listener(@NotNull Object session) {
		return listeners.get(session);
	}

	public synchronized @NotNull List<String> disconnects() {
		return List.copyOf(disconnects);
	}
}
