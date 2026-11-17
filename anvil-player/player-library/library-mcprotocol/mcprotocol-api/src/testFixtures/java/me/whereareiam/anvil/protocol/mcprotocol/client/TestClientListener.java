package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * A {@link ClientListener} that records what a client segment reports, for the tests of the segments and of what they
 * share. It writes each report as one line to its events, which a test may share with its own lines, such as the
 * packets its session sent:
 * {@code granted <name> <uuid>}, {@code logged in as <name> <uuid>}, {@code teleported <yaw> <pitch>} and
 * {@code disconnected <cause> <reason>}. It keeps the delivered packets and frames apart, wants every packet, and wants
 * frames once the test {@linkplain #wantFrames(boolean) asks for them}.
 *
 * <pre>{@code
 * TestClientListener<Session> listener = new TestClientListener<>();
 * session.addListener(new ClientPacketListener(login, listener, answers, text));
 * assertEquals(List.of("teleported 90.0 0.0"), listener.events());
 * }</pre>
 *
 * @param <S> native session type of the release
 */
public final class TestClientListener<S> implements ClientListener<S> {
	private final List<String> events;
	private final List<ClientboundPacket> received = new CopyOnWriteArrayList<>();
	private final List<Frame> frames = new CopyOnWriteArrayList<>();
	private final List<BiConsumer<DisconnectCause, String>> disconnects = new CopyOnWriteArrayList<>();
	private volatile boolean wantsFrames;

	/**
	 * Creates a listener with events of its own.
	 */
	public TestClientListener() {
		this(new CopyOnWriteArrayList<>());
	}

	/**
	 * Creates a listener that writes its events to a list the test shares.
	 *
	 * @param events the list the listener adds its events to
	 */
	public TestClientListener(@NotNull List<String> events) {
		this.events = events;
	}

	/**
	 * Returns the reports, in the order they arrived.
	 *
	 * @return the events
	 */
	public @NotNull List<String> events() {
		return List.copyOf(events);
	}

	/**
	 * Returns the packets delivered while no frames were wanted, in the order they arrived.
	 *
	 * @return the packets
	 */
	public @NotNull List<ClientboundPacket> received() {
		return List.copyOf(received);
	}

	/**
	 * Returns the frames delivered while frames were wanted, in the order they arrived.
	 *
	 * @return the frames
	 */
	public @NotNull List<Frame> frames() {
		return List.copyOf(frames);
	}

	/**
	 * Makes the listener want frames, or no longer want them.
	 *
	 * @param wanted whether frames are wanted
	 */
	public void wantFrames(boolean wanted) {
		wantsFrames = wanted;
	}

	/**
	 * Runs an action for each disconnect reported from now on, on the thread that reports it.
	 *
	 * @param action receives the cause and the reason
	 */
	public void onDisconnect(@NotNull BiConsumer<DisconnectCause, String> action) {
		disconnects.add(action);
	}

	@Override
	public void granted(@NotNull S session, @NotNull ClientProfile granted) {
		events.add("granted " + granted.getName() + " " + granted.getUniqueId());
	}

	@Override
	public void loggedIn(@NotNull S session, @NotNull ClientProfile granted) {
		events.add("logged in as " + granted.getName() + " " + granted.getUniqueId());
	}

	@Override
	public void teleported(@NotNull S session, float yaw, float pitch) {
		events.add("teleported " + yaw + " " + pitch);
	}

	@Override
	public void disconnected(@NotNull S session, @NotNull DisconnectCause cause, @NotNull String reason) {
		events.add("disconnected " + cause + " " + reason);
		for (BiConsumer<DisconnectCause, String> action : disconnects) action.accept(cause, reason);
	}

	@Override
	public void received(@NotNull S session, @NotNull ClientboundPacket packet) {
		received.add(packet);
	}

	@Override
	public boolean wantsFrames() {
		return wantsFrames;
	}

	@Override
	public void received(
			@NotNull S session,
			@NotNull ConnectionState state,
			int id,
			byte @NotNull [] payload,
			@Nullable ClientboundPacket packet
	) {
		frames.add(new Frame(state, id, payload.clone(), packet));
	}

	@Override
	public boolean wants(@NotNull Class<? extends ClientboundPacket> type) {
		return true;
	}

	/**
	 * One delivered frame.
	 *
	 * @param state the connection's state when the packet arrived
	 * @param id the packet's ID
	 * @param payload the packet's bytes after its ID
	 * @param packet the packet's translation, or {@code null}
	 */
	public record Frame(
			@NotNull ConnectionState state,
			int id,
			byte @NotNull [] payload,
			@Nullable ClientboundPacket packet
	) {
		@Override
		public boolean equals(Object other) {
			return other instanceof Frame frame && state == frame.state && id == frame.id
					&& Arrays.equals(payload, frame.payload) && Objects.equals(packet, frame.packet);
		}

		@Override
		public int hashCode() {
			return 31 * (31 * (31 * state.hashCode() + id) + Arrays.hashCode(payload)) + Objects.hashCode(packet);
		}

		@Override
		public @NotNull String toString() {
			return "Frame[" + state + " " + id + " " + Arrays.toString(payload) + " " + packet + "]";
		}
	}
}
