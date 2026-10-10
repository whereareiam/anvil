package me.whereareiam.anvil.api.model.player;

import me.whereareiam.anvil.api.type.AuthenticationMode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLoginTest {
	private static final SessionIdentity IDENTITY = SessionIdentity.builder()
			.username("Alice")
			.uniqueId(UUID.randomUUID())
			.accessToken("token")
			.sessionServer(URI.create("http://127.0.0.1:25580/session/minecraft"))
			.build();

	@Test
	void defaultsToAnOfflineLoginAsThePlayerName() {
		PlayerOptions options = PlayerOptions.builder().name("alice").build();

		assertSame(PlayerLogin.offline(), options.getLogin());
		assertEquals(AuthenticationMode.OFFLINE, options.getLogin().getAuthentication());
		assertNull(options.getLogin().getUsername());
		assertFalse(options.getLogin().isLeased());
	}

	@Test
	void offlineLoginCarriesOnlyAUsername() {
		PlayerLogin login = PlayerLogin.offline("Alice");

		assertEquals("Alice", login.getUsername());
		assertNull(login.getAccountId());
		assertNull(login.getSessionIdentity());
		assertThrows(IllegalArgumentException.class, () -> PlayerLogin.offline(" "));
	}

	@Test
	void accountLoginsRequireAnAccountModeAndANonBlankId() {
		PlayerLogin login = PlayerLogin.account(AuthenticationMode.ON_REQUEST, "main");

		assertEquals(AuthenticationMode.ON_REQUEST, login.getAuthentication());
		assertEquals("main", login.getAccountId());
		assertNull(login.getUsername());
		assertFalse(login.isLeased());
		assertThrows(IllegalArgumentException.class, () -> PlayerLogin.account(AuthenticationMode.OFFLINE, "main"));
		assertThrows(IllegalArgumentException.class, () -> PlayerLogin.account(AuthenticationMode.ONLINE, ""));
	}

	@Test
	void sessionLoginsReplaceTheAccountId() {
		PlayerLogin login = PlayerLogin.session(AuthenticationMode.ONLINE, IDENTITY);

		assertSame(IDENTITY, login.getSessionIdentity());
		assertNull(login.getAccountId());
		assertFalse(login.isLeased());
		assertThrows(IllegalArgumentException.class, () -> PlayerLogin.session(AuthenticationMode.OFFLINE, IDENTITY));
	}

	@Test
	void leasedLoginsWaitForTheirAccount() {
		PlayerLogin login = PlayerLogin.leased(AuthenticationMode.ONLINE);

		assertTrue(login.isLeased());
		assertNull(login.getAccountId());
		assertNull(login.getSessionIdentity());
		assertThrows(IllegalArgumentException.class, () -> PlayerLogin.leased(AuthenticationMode.OFFLINE));
	}
}
