package me.whereareiam.anvil.protocol.cloudburst;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XboxAuthenticationTest {
	@Test
	void refusesStoredCredentialsThatAreNoBedrockSessionWithoutQuotingThem() {
		AccountRecord account = AccountRecord.builder()
				.accountId("player")
				.username("Player")
				.uniqueId(UUID.randomUUID())
				.credentials("{\"refresh\":\"private-token\"}")
				.build();

		var failure = assertThrows(IllegalStateException.class,
				() -> new XboxAuthentication().resolve(account, MinecraftVersion.parse("1.21.50")));

		assertEquals("Invalid stored authentication account 'player'", failure.getMessage());
		assertNull(failure.getCause());
	}
}
