package me.whereareiam.anvil.protocol.java.worker;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.worker.TestWorkerPlayer;
import me.whereareiam.anvil.protocol.worker.WorkerProtocols;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A worker player that speaks the Java Edition protocol through the kit's own {@link JavaPacketDispatcher}, for the
 * tests of Java Edition worker extensions. The test plays the server: it {@link #deliver(ClientboundPacket) delivers}
 * the packets the player receives and reads the packets the player {@link #sent() sent}. Its release carries the
 * packets the test names, every packet unless it names some, and the dispatcher refuses the others as a library's
 * does.
 *
 * <p>Packets travel only on an open session: delivering or sending one while the player is disconnected fails, as no
 * library's connection carries it. Disconnecting tells the dispatcher, so the player's world forgets its level.</p>
 *
 * <pre>{@code
 * TestJavaPlayer player = new TestJavaPlayer("Alice");
 * extension.bind(player, new TestOperations());
 * player.connect();
 * player.deliver(ClientboundSound.builder().sound("minecraft:ui.toast.in").source(SoundSource.MASTER).build());
 * List<ServerboundChat> chat = player.sent(ServerboundChat.class);
 * }</pre>
 */
public final class TestJavaPlayer extends TestWorkerPlayer {
	/**
	 * The protocol a test player speaks unless its test names another: 774, the protocol of Minecraft 1.21.11.
	 */
	public static final int PROTOCOL = 774;

	private final Predicate<Class<? extends JavaPacket>> carried;
	private final List<ServerboundPacket> sent = new CopyOnWriteArrayList<>();
	private final JavaPacketDispatcher protocol;
	private final WorkerProtocols protocols;

	/**
	 * Creates a disconnected player of the {@link #PROTOCOL default protocol} whose release carries every packet.
	 *
	 * @param name the player's name
	 */
	public TestJavaPlayer(@NotNull String name) {
		this(name, type -> true);
	}

	/**
	 * Creates a disconnected player of the {@link #PROTOCOL default protocol} whose release carries only some packets.
	 *
	 * @param name the player's name
	 * @param carried tells whether the release carries a packet class
	 */
	public TestJavaPlayer(@NotNull String name, @NotNull Predicate<Class<? extends JavaPacket>> carried) {
		this(name, PROTOCOL, carried);
	}

	/**
	 * Creates a disconnected player of a protocol whose release carries only some packets.
	 *
	 * @param name the player's name
	 * @param protocolNumber the protocol the player speaks
	 * @param carried tells whether the release carries a packet class
	 */
	public TestJavaPlayer(
			@NotNull String name,
			int protocolNumber,
			@NotNull Predicate<Class<? extends JavaPacket>> carried
	) {
		super(name);
		this.carried = carried;
		this.protocol = new JavaPacketDispatcher(LIBRARY_ID, protocolNumber, new TestConnection());
		this.protocols = WorkerProtocols.builder(LIBRARY_ID)
				.speak(JavaProtocol.class, protocol, protocol::scoped)
				.build();
	}

	/**
	 * Returns the player's protocol, as its extensions get it.
	 *
	 * @return the dispatcher
	 */
	public @NotNull JavaPacketDispatcher protocol() {
		return protocol;
	}

	/**
	 * Hands a packet the server sent to the player's world and the listeners of its class.
	 *
	 * @param packet the received packet
	 * @throws IllegalStateException when the player is not connected
	 */
	public void deliver(@NotNull ClientboundPacket packet) {
		nativeSession();
		protocol.deliver(packet);
	}

	/**
	 * Returns every packet the player sent, in the order it sent them.
	 *
	 * @return the sent packets
	 */
	public @NotNull List<ServerboundPacket> sent() {
		return List.copyOf(sent);
	}

	/**
	 * Returns the packets of one class the player sent, in the order it sent them.
	 *
	 * @param type the packet class
	 * @param <P> packet type
	 * @return the sent packets of that class
	 */
	public <P extends ServerboundPacket> @NotNull List<P> sent(@NotNull Class<P> type) {
		return sent.stream().filter(type::isInstance).map(type::cast).toList();
	}

	@Override
	protected @NotNull WorkerProtocols protocols() {
		return protocols;
	}

	@Override
	protected void disconnected() {
		protocol.disconnected();
	}

	/**
	 * Records the packets the player sends on its open session, as far as its release carries them.
	 */
	private final class TestConnection implements JavaConnection {
		@Override
		public void send(@NotNull ServerboundPacket packet) {
			if (!carried.test(packet.getClass()))
				throw new ProtocolUnavailableException("the loaded " + LIBRARY_ID + " release cannot send "
						+ packet.getClass().getName());

			nativeSession();
			sent.add(packet);
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return carried.test(type);
		}
	}
}
