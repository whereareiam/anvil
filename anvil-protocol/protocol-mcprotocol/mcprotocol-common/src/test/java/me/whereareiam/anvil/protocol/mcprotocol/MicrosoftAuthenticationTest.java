package me.whereareiam.anvil.protocol.mcprotocol;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MicrosoftAuthenticationTest {
	@Test
	void refusesStoredCredentialsThatAreNoJavaSessionWithoutQuotingThem() {
		AccountRecord account = AccountRecord.builder()
				.accountId("player")
				.username("Player")
				.uniqueId(UUID.randomUUID())
				.credentials("{\"refresh\":\"private-token\"}")
				.build();

		var failure = assertThrows(IllegalStateException.class,
				() -> new MicrosoftAuthentication().resolve(account, MinecraftVersion.parse("1.21.11")));

		assertEquals("Invalid stored authentication account 'player'", failure.getMessage());
		assertNull(failure.getCause());
	}
}
