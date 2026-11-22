package me.whereareiam.anvil.edition.java.protocol.worker;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPacketDispatcherTest {
	private final RecordingConnection connection = new RecordingConnection(true);
	private final JavaPacketDispatcher protocol = dispatcher(774, connection);

	@Test
	void wantsThePacketsOfItsListenersWhileTheyListen() {
		assertFalse(protocol.wants(ClientboundSystemChat.class));

		Subscription chat = protocol.listen(ClientboundSystemChat.class, packet -> {
		});
		assertTrue(protocol.wants(ClientboundSystemChat.class));

		chat.close();
		assertFalse(protocol.wants(ClientboundSystemChat.class));
	}

	@Test
	void deliversAPacketOnlyToTheListenersOfItsClass() {
		List<String> received = new ArrayList<>();
		protocol.listen(ClientboundSystemChat.class, packet -> received.add("first " + packet.getText().plain()));
		protocol.listen(ClientboundSystemChat.class, packet -> received.add("second " + packet.getText().plain()));
		protocol.listen(ClientboundPlayerChat.class, packet -> received.add("player " + packet.getText().plain()));

		protocol.deliver(new ClientboundSystemChat(Text.literal("welcome"), false));

		assertEquals(List.of("first welcome", "second welcome"), received);
	}

	@Test
	void stopsDeliveringToAClosedSubscription() {
		List<ClientboundSystemChat> closed = new ArrayList<>();
		List<ClientboundSystemChat> open = new ArrayList<>();
		Subscription subscription = protocol.listen(ClientboundSystemChat.class, closed::add);
		protocol.listen(ClientboundSystemChat.class, open::add);
		ClientboundSystemChat message = new ClientboundSystemChat(Text.literal("welcome"), false);

		subscription.close();
		protocol.deliver(message);

		assertEquals(List.of(), closed);
		assertEquals(List.of(message), open);
	}

	@Test
	void keepsDeliveringToTheOtherListenersWhenOneFails() {
		List<ClientboundSystemChat> received = new ArrayList<>();
		protocol.listen(ClientboundSystemChat.class, packet -> {
			throw new IllegalStateException("listener failed");
		});
		protocol.listen(ClientboundSystemChat.class, received::add);
		ClientboundSystemChat message = new ClientboundSystemChat(Text.literal("welcome"), false);

		protocol.deliver(message);

		assertEquals(List.of(message), received);
	}

	@Test
	void handsEachSentPacketToTheListenersOfSentPacketsOfItsClassAfterSendingIt() {
		List<String> observed = new ArrayList<>();
		Subscription subscription = protocol.listenSent(ServerboundChat.class, packet ->
				observed.add(packet.getText() + " after " + connection.sent.size()));

		protocol.send(new ServerboundChat("first"));
		subscription.close();
		protocol.send(new ServerboundChat("second"));

		assertEquals(List.of("first after 1"), observed);
		assertEquals(List.of(new ServerboundChat("first"), new ServerboundChat("second")), connection.sent);
	}

	@Test
	void refusesToSendOrListenToAPacketTheReleaseDoesNotCarry() {
		var refused = assertThrows(ProtocolUnavailableException.class,
				() -> protocol.listen(ClientboundDisguisedChat.class, packet -> { }));
		assertEquals("the loaded test release does not carry " + ClientboundDisguisedChat.class.getName(),
				refused.getMessage());
		assertThrows(ProtocolUnavailableException.class, () -> protocol.send(new ServerboundChatCommand("anvil ping")));
		assertThrows(ProtocolUnavailableException.class,
				() -> protocol.listenSent(ServerboundChatCommand.class, packet -> { }));
		assertEquals(List.of(), connection.sent);
	}

	@Test
	void supportsWhatTheConnectionCarries() {
		assertTrue(protocol.supports(ClientboundSystemChat.class));
		assertTrue(protocol.supports(ServerboundChat.class));
		assertFalse(protocol.supports(ClientboundDisguisedChat.class));
		assertFalse(protocol.supports(ServerboundChatCommand.class));
	}

	@Test
	void aScopedViewReportsEverySubscriptionItCreates() {
		List<Subscription> tracked = new ArrayList<>();
		List<String> received = new ArrayList<>();
		var view = protocol.scoped(tracked::add);

		view.listen(ClientboundSystemChat.class, packet -> received.add(packet.getText().plain()));
		view.listenSent(ServerboundChat.class, packet -> received.add(packet.getText()));
		view.send(new ServerboundChat("sent"));
		protocol.deliver(new ClientboundSystemChat(Text.literal("received"), false));
		view.player().onTick(() -> received.add("ticked"));
		tracked.forEach(Subscription::close);
		protocol.deliver(new ClientboundSystemChat(Text.literal("after close"), false));

		assertEquals(3, tracked.size());
		assertEquals(List.of("sent", "received"), received);
	}

	@Test
	void wantsFramesAndEveryPacketButTheCostlyOnesWhileAFrameListenerListens() {
		assertFalse(protocol.wantsFrames());

		Subscription frames = protocol.listenFrames(frame -> {
		});
		assertTrue(protocol.wantsFrames());
		assertTrue(protocol.wants(ClientboundSystemChat.class));
		assertFalse(protocol.wants(ClientboundLevelChunkWithLight.class));

		frames.close();
		assertFalse(protocol.wantsFrames());
		assertFalse(protocol.wants(ClientboundSystemChat.class));
	}

	@Test
	void wantsACostlyPacketWhileSomethingWantsItsClass() {
		Subscription frames = protocol.listenFrames(frame -> {
		});
		Subscription chunks = protocol.want(ClientboundLevelChunkWithLight.class);
		Subscription again = protocol.want(ClientboundLevelChunkWithLight.class);
		assertTrue(protocol.wants(ClientboundLevelChunkWithLight.class));

		chunks.close();
		assertTrue(protocol.wants(ClientboundLevelChunkWithLight.class));
		again.close();
		assertFalse(protocol.wants(ClientboundLevelChunkWithLight.class));
		frames.close();
	}

	@Test
	void deliversAFrameNamedByThePlayersProtocolAfterItsValue() {
		List<String> received = new ArrayList<>();
		protocol.listen(ClientboundSystemChat.class, packet -> received.add("value " + packet.getText().plain()));
		protocol.listenFrames(frame -> received.add("frame " + frame.getState() + " " + frame.getId() + " "
				+ frame.name().orElseThrow() + " " + frame.size() + " " + frame.packet().isPresent()));
		ClientboundSystemChat welcome = new ClientboundSystemChat(Text.literal("welcome"), false);

		protocol.deliver(ConnectionState.PLAY, 0x08, new byte[]{1, 2, 3}, null);
		protocol.deliver(ConnectionState.PLAY, 0x77, new byte[]{4}, welcome);

		assertEquals(List.of("frame PLAY 8 block_change 3 false", "value welcome", "frame PLAY 119 system_chat 1 true"),
				received);
	}

	@Test
	void leavesTheFramesOfAProtocolWithoutPacketDataUnnamed() {
		JavaPacketDispatcher unnamed = dispatcher(777, connection);
		List<ClientboundFrame> frames = new ArrayList<>();
		unnamed.listenFrames(frames::add);

		unnamed.deliver(ConnectionState.PLAY, 0, new byte[0], null);

		assertEquals(Optional.empty(), frames.getFirst().name());
	}

	@Test
	void keepsDeliveringFramesPastAFailingListener() {
		List<ClientboundFrame> frames = new ArrayList<>();
		protocol.listenFrames(frame -> {
			throw new IllegalStateException("broken");
		});
		protocol.listenFrames(frames::add);

		protocol.deliver(ConnectionState.LOGIN, 0, new byte[0], null);

		assertEquals(1, frames.size());
	}

	@Test
	void refusesFrameListenersOfALibraryThatHandsOverNoFrames() {
		JavaPacketDispatcher unframed = dispatcher(774, new RecordingConnection(false));

		ProtocolUnavailableException failure = assertThrows(ProtocolUnavailableException.class,
				() -> unframed.listenFrames(frame -> {
				}));

		assertEquals("the test library does not hand over the frames its players receive", failure.getMessage());
	}

	/**
	 * Creates the protocol of a player whose client of 1.21.1 reads no chunks itself, so only the test's listeners and
	 * wants decide what is wanted.
	 */
	private static @NotNull JavaPacketDispatcher dispatcher(int protocol, @NotNull JavaConnection connection) {
		return new JavaPacketDispatcher("test", protocol, MinecraftVersion.parse("1.21.1"), "Alice", connection);
	}

	private static final class RecordingConnection implements JavaConnection {
		private static final Set<Class<? extends JavaPacket>> CARRIED = Set.of(
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class,
				ClientboundLevelChunkWithLight.class,
				ServerboundChat.class
		);

		private final List<ServerboundPacket> sent = new ArrayList<>();
		private final boolean framed;

		private RecordingConnection(boolean framed) {
			this.framed = framed;
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			if (!supports(packet.getClass())) throw new ProtocolUnavailableException("cannot send " + packet);

			sent.add(packet);
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return CARRIED.contains(type);
		}

		@Override
		public boolean handsOverFrames() {
			return framed;
		}
	}
}
