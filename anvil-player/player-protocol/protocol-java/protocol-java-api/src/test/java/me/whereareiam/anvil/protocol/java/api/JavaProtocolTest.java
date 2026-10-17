package me.whereareiam.anvil.protocol.java.api;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundCookieRequest;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundStoreCookie;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ServerboundCookieResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaProtocolTest {
	private static final List<Class<? extends JavaPacket>> COOKIES = List.of(
			ClientboundCookieRequest.class,
			ClientboundStoreCookie.class,
			ServerboundCookieResponse.class
	);

	@Test
	void requiresNothingMoreThanTheReleaseCarries() {
		JavaProtocol protocol = new CarryingProtocol(Set.copyOf(COOKIES));

		assertDoesNotThrow(() -> protocol.require("cookies", COOKIES));
	}

	@Test
	void namesTheFeatureAndTheFirstPacketTheReleaseDoesNotCarry() {
		JavaProtocol protocol = new CarryingProtocol(Set.of(ClientboundCookieRequest.class));

		ProtocolUnavailableException failure = assertThrows(ProtocolUnavailableException.class,
				() -> protocol.require("cookies", COOKIES));

		assertEquals("the player's release has no cookies; it does not carry " + ClientboundStoreCookie.class.getName(),
				failure.getMessage());
	}

	/**
	 * A protocol that carries the given packets and does nothing else.
	 */
	private record CarryingProtocol(Set<Class<? extends JavaPacket>> carried) implements JavaProtocol {
		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return carried.contains(type);
		}
	}
}
