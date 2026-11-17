package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.capability.session.model.SessionState;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.player.PlayerConnectionEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelSessionTest {
	private static final PlayerProfile ALICE = new PlayerProfile("Alice", UUID.randomUUID());
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(SessionProvider.ID));

	ChannelSessionTest() {
		channel.answer(SessionOperations.CONNECT, ignored -> null);
		channel.answer(SessionOperations.REJOIN, ignored -> null);
		channel.answer(SessionOperations.DISCONNECT, ignored -> null);
	}

	@Test
	void requestsLifecycleOperationsFromTheWorker() {
		ChannelSession session = new ChannelSession(channel);

		session.connect();
		session.rejoin();
		session.disconnect();

		assertEquals(List.of("session.connect", "session.rejoin", "session.disconnect"), channel.requests());
	}

	@Test
	void followsConnectionEventsAndKeepsTheKickReason() {
		ChannelSession session = new ChannelSession(channel);

		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null, null, ALICE));
		session.connected(TIMEOUT);
		assertEquals(SessionState.builder().connected(true).build(), session.state());

		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, "Server closed", DisconnectCause.SERVER, null));
		session.disconnected(TIMEOUT);
		assertEquals("Server closed", session.kicked(TIMEOUT));
		assertEquals(SessionState.builder().connected(false).kickReason("Server closed").disconnectCause(DisconnectCause.SERVER).build(),
				session.state());
	}

	@Test
	void forgetsTheKickReasonWhenConnectingAgain() {
		ChannelSession session = new ChannelSession(channel);
		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, "Server closed", DisconnectCause.SERVER, null));

		session.rejoin();

		assertEquals(SessionState.builder().connected(false).build(), session.state());
		assertThrows(IllegalStateException.class, () -> session.kicked(TIMEOUT));
	}

	@Test
	void expectsAKickAndADisconnectFromTheObservedState() {
		ChannelSession session = new ChannelSession(channel);
		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null, null, ALICE));
		Expectation kicked = session.expectKicked();
		Expectation disconnected = session.expectDisconnected();

		assertFalse(kicked.met());
		assertFalse(disconnected.met());

		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, "Reconnect", DisconnectCause.SERVER, null));
		assertTrue(kicked.met());
		assertTrue(disconnected.met());
		assertEquals("Reconnect", session.kicked(TIMEOUT));

		session.rejoin();
		assertFalse(kicked.met(), "a new session is not kicked yet");
	}

	@Test
	void reportsDisconnectedOnceThePlayerIsDestroyed() {
		ChannelSession session = new ChannelSession(channel);
		channel.publish(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null, null, ALICE));

		channel.publish(PlayerConnectionEvent.DESTROYED, null);

		session.disconnected(TIMEOUT);
		assertEquals(SessionState.builder().connected(false).build(), session.state());
	}
}
