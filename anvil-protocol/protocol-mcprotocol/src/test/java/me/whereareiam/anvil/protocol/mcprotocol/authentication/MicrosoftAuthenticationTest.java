package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MicrosoftAuthenticationTest {
	@TempDir
	Path temporary;

	@Test
	void reportsMissingProfilesWithoutAttemptingOnlineLogin() {
		var authentication = new MicrosoftAuthentication(temporary);
		var failure = assertThrows(IllegalStateException.class, () -> authentication.resolve("missing"));
		assertTrue(failure.getMessage().contains("--auth-profile=missing"));
	}

	@Test
	void doesNotExposeCorruptProfileContentsInFailures() throws Exception {
		new AuthenticationProfileStore(temporary).write("corrupt", "{\"token\":\"private-token\",BROKEN");
		var failure = assertThrows(IllegalStateException.class,
				() -> new MicrosoftAuthentication(temporary).resolve("corrupt"));
		assertEquals("Invalid stored authentication profile 'corrupt'", failure.getMessage());
		assertNull(failure.getCause());
	}
}
