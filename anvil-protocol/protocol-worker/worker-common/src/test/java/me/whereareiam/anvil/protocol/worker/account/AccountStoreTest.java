package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.type.AccountAvailability;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import me.whereareiam.anvil.protocol.worker.account.AccountStore.LibraryEntry;
import me.whereareiam.anvil.protocol.worker.account.AccountStore.StoredAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {
	static final MicrosoftToken TOKEN = MicrosoftToken.builder()
			.clientId("client")
			.scope("scope")
			.accessToken("secret-access")
			.refreshToken("secret-refresh")
			.expiresAt(Instant.ofEpochMilli(1_000))
			.build();

	@TempDir
	Path temporary;

	@Test
	void writesAccountsWithOwnerOnlyPermissionsAndReadsThemBack() throws Exception {
		AccountStore store = new AccountStore(temporary);
		UUID uuid = UUID.randomUUID();
		store.write(StoredAccount.builder()
				.accountId("developer")
				.token(TOKEN)
				.library("javalib", new LibraryEntry(AccountAccess.available("javalib", "Developer", uuid), "{\"chain\":1}"))
				.library("bedrocklib", new LibraryEntry(AccountAccess.unavailable("bedrocklib", "Account 'developer' owns no game"), null))
				.build());
		Path account = store.accountFile("developer");

		if (Files.getFileStore(account).supportsFileAttributeView("posix"))
			assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(account));
		StoredAccount read = store.read("developer");
		assertEquals(TOKEN, read.getToken());
		assertEquals("{\"chain\":1}", read.cachedSession("javalib"));
		assertNull(read.cachedSession("bedrocklib"));
		assertEquals(AccountAccess.available("javalib", "Developer", uuid), read.getLibraries().get("javalib").access());
		assertEquals("Account 'developer' owns no game", read.getLibraries().get("bedrocklib").access().getReason());
		assertTrue(store.delete("developer"));
		assertFalse(store.delete("developer"));
		assertFalse(Files.exists(account));
	}

	@Test
	void rejectsPathTraversalAccountNames() {
		AccountStore store = new AccountStore(temporary);

		assertThrows(IllegalArgumentException.class, () -> store.accountFile("../credentials"));
	}

	@Test
	void listsEveryAccountWithWhatEachLibraryRecordedAndNoCredentials() throws Exception {
		AccountStore store = new AccountStore(temporary);
		UUID uuid = UUID.randomUUID();
		store.write(StoredAccount.builder()
				.accountId("alice")
				.token(TOKEN)
				.library("javalib", new LibraryEntry(AccountAccess.available("javalib", "Alice", uuid), null))
				.build());
		store.write(StoredAccount.builder().accountId("bob").token(TOKEN).build());
		Files.writeString(temporary.resolve("broken.json"), "{not json");

		List<AuthenticationAccount> accounts = store.accounts();

		assertEquals(List.of("alice", "bob"), accounts.stream().map(AuthenticationAccount::getAccountId).toList());
		assertEquals(AccountAvailability.AVAILABLE, accounts.getFirst().access("javalib").getAvailability());
		assertEquals("Alice", accounts.getFirst().access("javalib").getUsername());
		assertEquals(AccountAvailability.UNKNOWN, accounts.getFirst().access("bedrocklib").getAvailability());
		assertEquals(List.of(), accounts.getLast().getAccess());
	}

	@Test
	void readsAnAccountStoredForOneLibraryIntoTheMicrosoftAccountWithoutANewSignIn() throws Exception {
		try (InputStream legacy = getClass().getResourceAsStream("legacy.json")) {
			Files.write(temporary.resolve("legacy.json"), legacy.readAllBytes());
		}
		AccountStore store = new AccountStore(temporary);

		StoredAccount account = store.read("legacy");

		assertEquals("00000000402b5328", account.getToken().getClientId());
		assertEquals("service::user.auth.xboxlive.com::MBI_SSL", account.getToken().getScope());
		assertEquals("fixture-msa-access-token", account.getToken().getAccessToken());
		assertEquals("fixture-msa-refresh-token", account.getToken().getRefreshToken());
		assertEquals(Instant.ofEpochMilli(32_503_680_000_000L), account.getToken().getExpiresAt());
		AccountAccess access = account.getLibraries().get("mcprotocol").access();
		assertEquals(AccountAccess.available("mcprotocol", "Fixture", UUID.fromString("5f8eb73b-25be-4c5a-a50f-d27d65e30ca0")), access);
		assertTrue(account.cachedSession("mcprotocol").contains("\"minecraftProfile\""));

		store.write(account);

		assertTrue(Files.readString(temporary.resolve("legacy.json")).startsWith("{\"schemaVersion\":2,"));
		assertEquals(account, store.read("legacy"));
	}

	@Test
	void refusesAnAccountStoredForOneLibraryThatHoldsNoMicrosoftTokenWithoutQuotingIt() throws Exception {
		Files.writeString(temporary.resolve("old.json"), "{\"schemaVersion\":1,\"accountId\":\"old\",\"provider\":\"javalib\","
				+ "\"credentials\":{\"refresh\":\"secret\"}}");

		var failure = assertThrows(IllegalStateException.class, () -> new AccountStore(temporary).read("old"));

		assertEquals("Stored account 'old' holds no Microsoft token; sign it in again", failure.getMessage());
	}

	@Test
	void namesTheAccountAndDirectoryForMissingAccountsAndReportsAnUnknownSchema() throws IOException {
		Files.writeString(temporary.resolve("future.json"), "{\"schemaVersion\":3}");
		AccountStore store = new AccountStore(temporary);

		var missing = assertThrows(IllegalStateException.class, () -> store.read("missing"));
		var future = assertThrows(IllegalStateException.class, () -> store.read("future"));

		assertEquals("No stored account 'missing' in " + temporary.toAbsolutePath().normalize(), missing.getMessage());
		assertEquals("Stored account 'future' uses schema version 3; this Anvil version reads versions 1 and 2", future.getMessage());
	}

	@Test
	void reportsMalformedFilesWithoutQuotingThem() throws IOException {
		Files.writeString(temporary.resolve("broken.json"), "{\"secret\":");

		var failure = assertThrows(IllegalStateException.class, () -> new AccountStore(temporary).read("broken"));

		assertEquals("Stored account 'broken' is not a valid JSON object", failure.getMessage());
	}
}
