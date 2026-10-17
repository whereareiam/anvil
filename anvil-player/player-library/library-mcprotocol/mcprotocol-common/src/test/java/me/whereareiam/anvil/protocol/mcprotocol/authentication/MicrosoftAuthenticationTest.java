package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MicrosoftAuthenticationTest {
	@TempDir
	Path temporary;

	@Test
	void reportsMissingAccountsWithoutAttemptingOnlineLogin() {
		var authentication = new MicrosoftAuthentication(temporary);
		var failure = assertThrows(IllegalStateException.class, () -> authentication.resolve("missing"));
		assertTrue(failure.getMessage().startsWith("No stored account 'missing'"));
	}

	@Test
	void doesNotExposeCorruptAccountContentsInFailures() throws Exception {
		Files.createDirectories(temporary);
		Files.writeString(temporary.resolve("corrupt.json"), "{\"token\":\"private-token\",BROKEN");
		var failure = assertThrows(IllegalStateException.class,
				() -> new MicrosoftAuthentication(temporary).resolve("corrupt"));
		assertEquals("Stored account 'corrupt' is not a valid JSON object", failure.getMessage());
		assertFalse(failure.getMessage().contains("private-token"));
		assertNull(failure.getCause());
	}
}
