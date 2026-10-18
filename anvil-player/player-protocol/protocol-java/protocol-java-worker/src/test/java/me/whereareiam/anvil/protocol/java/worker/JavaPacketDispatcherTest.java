package me.whereareiam.anvil.protocol.java.worker;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPacketDispatcherTest {
	private final RecordingConnection connection = new RecordingConnection();
	private final JavaPacketDispatcher protocol = new JavaPacketDispatcher("test", connection);

	@Test
	void deliversAPacketOnlyToTheListenersOfItsClass() {
		List<String> received = new ArrayList<>();
		protocol.listen(ClientboundSystemChat.class, packet -> received.add("first " + packet.getText()));
		protocol.listen(ClientboundSystemChat.class, packet -> received.add("second " + packet.getText()));
		protocol.listen(ClientboundPlayerChat.class, packet -> received.add("player " + packet.getText()));

		protocol.deliver(new ClientboundSystemChat("welcome", false));

		assertEquals(List.of("first welcome", "second welcome"), received);
	}

	@Test
	void stopsDeliveringToAClosedSubscription() {
		List<ClientboundSystemChat> closed = new ArrayList<>();
		List<ClientboundSystemChat> open = new ArrayList<>();
		Subscription subscription = protocol.listen(ClientboundSystemChat.class, closed::add);
		protocol.listen(ClientboundSystemChat.class, open::add);
		ClientboundSystemChat message = new ClientboundSystemChat("welcome", false);

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
		ClientboundSystemChat message = new ClientboundSystemChat("welcome", false);

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

		view.listen(ClientboundSystemChat.class, packet -> received.add(packet.getText()));
		view.listenSent(ServerboundChat.class, packet -> received.add(packet.getText()));
		view.send(new ServerboundChat("sent"));
		protocol.deliver(new ClientboundSystemChat("received", false));
		tracked.forEach(Subscription::close);
		protocol.deliver(new ClientboundSystemChat("after close", false));

		assertEquals(2, tracked.size());
		assertEquals(List.of("sent", "received"), received);
	}

	private static final class RecordingConnection implements JavaConnection {
		private static final Set<Class<? extends JavaPacket>> CARRIED = Set.of(
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class,
				ServerboundChat.class
		);

		private final List<ServerboundPacket> sent = new ArrayList<>();

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			if (!supports(packet.getClass())) throw new ProtocolUnavailableException("cannot send " + packet);

			sent.add(packet);
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return CARRIED.contains(type);
		}
	}
}
