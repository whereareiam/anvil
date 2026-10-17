package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
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
public final class RecordingClient implements McProtocolClient<Object> {
	private final List<ClientLogin> logins = new ArrayList<>();
	private final Map<Object, ClientListener<? super Object>> listeners = new LinkedHashMap<>();
	private final Set<Object> connected = new LinkedHashSet<>();
	private final List<String> disconnects = new ArrayList<>();

	@Override
	public int protocolNumber() {
		return 774;
	}

	@Override
	public @NotNull Class<Object> sessionType() {
		return Object.class;
	}

	@Override
	public synchronized @NotNull Object open(@NotNull ClientLogin login, @NotNull ClientListener<? super Object> listener) {
		Object session = new Object();
		logins.add(login);
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

	public synchronized @NotNull List<ClientLogin> logins() {
		return List.copyOf(logins);
	}

	public synchronized @NotNull List<Object> sessions() {
		return List.copyOf(listeners.keySet());
	}

	public synchronized @NotNull ClientListener<? super Object> listener(@NotNull Object session) {
		return listeners.get(session);
	}

	public synchronized @NotNull List<String> disconnects() {
		return List.copyOf(disconnects);
	}
}
