package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.RecordingClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPacketChannelTest {
	private final RecordingClient client = new RecordingClient();
	private final AtomicReference<Object> session = new AtomicReference<>(new Object());
	private final JavaPacketChannel channel = new JavaPacketChannel(client, session::get);

	@Test
	void deliversAPacketOnlyToTheListenersOfItsClass() {
		List<String> received = new ArrayList<>();
		channel.listen(ClientboundSystemChat.class, packet -> received.add("first " + packet.getText()));
		channel.listen(ClientboundSystemChat.class, packet -> received.add("second " + packet.getText()));
		channel.listen(ClientboundPlayerChat.class, packet -> received.add("player " + packet.getText()));

		channel.deliver(new ClientboundSystemChat("welcome", false));

		assertEquals(List.of("first welcome", "second welcome"), received);
	}

	@Test
	void stopsDeliveringToAClosedSubscription() {
		List<ClientboundSystemChat> closed = new ArrayList<>();
		List<ClientboundSystemChat> open = new ArrayList<>();
		Subscription subscription = channel.listen(ClientboundSystemChat.class, closed::add);
		channel.listen(ClientboundSystemChat.class, open::add);
		ClientboundSystemChat message = new ClientboundSystemChat("welcome", false);

		subscription.close();
		channel.deliver(message);

		assertEquals(List.of(), closed);
		assertEquals(List.of(message), open);
	}

	@Test
	void keepsDeliveringToTheOtherListenersWhenOneFails() {
		List<ClientboundSystemChat> received = new ArrayList<>();
		channel.listen(ClientboundSystemChat.class, packet -> {
			throw new IllegalStateException("listener failed");
		});
		channel.listen(ClientboundSystemChat.class, received::add);
		ClientboundSystemChat message = new ClientboundSystemChat("welcome", false);

		channel.deliver(message);

		assertEquals(List.of(message), received);
	}

	@Test
	void sendsOnTheSessionThatIsCurrentWhenSending() {
		Object first = session.get();
		channel.send(new ServerboundChat("first"));
		Object second = new Object();
		session.set(second);
		channel.send(new ServerboundChat("second"));

		assertEquals(List.of(new ServerboundChat("first")), client.sent(first));
		assertEquals(List.of(new ServerboundChat("second")), client.sent(second));
	}

	@Test
	void refusesToSendOrListenToAPacketTheReleaseDoesNotCarry() {
		assertThrows(ProtocolUnavailableException.class, () -> channel.send(new ServerboundChatCommand("anvil-fixture ping")));
		assertThrows(ProtocolUnavailableException.class, () -> channel.listen(ClientboundDisguisedChat.class, packet -> { }));
		assertEquals(List.of(), client.sent(session.get()));
	}

	@Test
	void supportsWhatTheClientCarries() {
		assertTrue(channel.supports(ClientboundSystemChat.class));
		assertTrue(channel.supports(ServerboundChat.class));
		assertFalse(channel.supports(ClientboundDisguisedChat.class));
		assertFalse(channel.supports(ServerboundChatCommand.class));
	}
}
