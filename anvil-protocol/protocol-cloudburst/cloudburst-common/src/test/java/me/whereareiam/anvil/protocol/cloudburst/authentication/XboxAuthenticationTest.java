package me.whereareiam.anvil.protocol.cloudburst.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XboxAuthenticationTest {
	@TempDir
	Path temporary;

	@Test
	void reportsMissingAccountsWithoutAttemptingOnlineLogin() {
		var failure = assertThrows(IllegalStateException.class, () -> new XboxAuthentication(temporary).resolve("missing"));

		assertTrue(failure.getMessage().startsWith("No stored account 'missing'"), failure.getMessage());
	}

	@Test
	void doesNotExposeCorruptAccountContentsInFailures() throws Exception {
		Files.writeString(temporary.resolve("corrupt.json"), "{\"token\":\"private-token\",BROKEN");

		var failure = assertThrows(IllegalStateException.class, () -> new XboxAuthentication(temporary).resolve("corrupt"));

		assertEquals("Stored account 'corrupt' is not a valid JSON object", failure.getMessage());
		assertFalse(failure.getMessage().contains("private-token"));
		assertNull(failure.getCause());
	}

	@Test
	void refusesAStoredDocumentThatIsNoBedrockSessionWithoutQuotingIt() throws Exception {
		new AuthenticationAccountStore(temporary).write("player", "cloudburst", "Player", UUID.randomUUID(), "{\"refresh\":\"private-token\"}");

		var failure = assertThrows(IllegalStateException.class, () -> new XboxAuthentication(temporary).resolve("player"));

		assertEquals("Invalid stored authentication account 'player'", failure.getMessage());
		assertNull(failure.getCause());
	}

	@Test
	void listsOnlyTheAccountsOfThisLibrary() throws Exception {
		AuthenticationAccountStore store = new AuthenticationAccountStore(temporary);
		store.write("bedrock", "cloudburst", "Bedrock", UUID.randomUUID(), "{}");
		store.write("java", "mcprotocol", "Java", UUID.randomUUID(), "{}");
		XboxAuthentication authentication = new XboxAuthentication(temporary);

		assertEquals(List.of("bedrock"), authentication.accounts().stream().map(account -> account.getAccountId()).toList());

		List<String> output = new ArrayList<>();
		authentication.logout("bedrock", output::add);
		authentication.logout("bedrock", output::add);
		assertEquals(List.of("Removed authentication account 'bedrock'.", "Authentication account 'bedrock' did not exist."), output);
	}
}
