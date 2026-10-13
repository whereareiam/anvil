package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.type.AccountAvailability;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {
	static final Edition JAVA = Edition.of("java");
	static final Edition BEDROCK = Edition.of("bedrock");
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
		AccountStore store = store();
		UUID uuid = UUID.randomUUID();
		store.write(StoredAccount.of("developer", TOKEN)
				.withAccess(AccountAccess.available(JAVA, "Developer", uuid))
				.withAccess(AccountAccess.unavailable(BEDROCK, "Account 'developer' owns no game"))
				.withSession("javalib", "{\"chain\":1}"));
		Path account = store.accountFile("developer");

		if (Files.getFileStore(account).supportsFileAttributeView("posix"))
			assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(account));
		StoredAccount read = store.read("developer");
		assertEquals(TOKEN, read.getToken());
		assertEquals("{\"chain\":1}", read.cachedSession("javalib"));
		assertNull(read.cachedSession("bedrocklib"));
		assertEquals(AccountAccess.available(JAVA, "Developer", uuid), read.access(JAVA));
		assertEquals("Account 'developer' owns no game", read.access(BEDROCK).getReason());
		assertTrue(store.delete("developer"));
		assertFalse(store.delete("developer"));
		assertFalse(Files.exists(account));
	}

	@Test
	void rejectsPathTraversalAccountNames() {
		AccountStore store = store();

		assertThrows(IllegalArgumentException.class, () -> store.accountFile("../credentials"));
	}

	@Test
	void listsEveryAccountWithWhatWasRecordedForEachEditionAndNoCredentials() throws Exception {
		AccountStore store = store();
		UUID uuid = UUID.randomUUID();
		store.write(StoredAccount.of("alice", TOKEN).withAccess(AccountAccess.available(JAVA, "Alice", uuid)));
		store.write(StoredAccount.of("bob", TOKEN));
		Files.writeString(temporary.resolve("broken.json"), "{not json");

		List<AuthenticationAccount> accounts = store.accounts();

		assertEquals(List.of("alice", "bob"), accounts.stream().map(AuthenticationAccount::getAccountId).toList());
		assertEquals(AccountAvailability.AVAILABLE, accounts.getFirst().access(JAVA).getAvailability());
		assertEquals("Alice", accounts.getFirst().access(JAVA).getUsername());
		assertEquals(AccountAvailability.UNKNOWN, accounts.getFirst().access(BEDROCK).getAvailability());
		assertEquals(List.of(), accounts.getLast().getAccess());
	}

	@Test
	void readsWhatEachLibraryRecordedAsTheRecordOfItsEditionAndKeepsEachCachedSession() throws Exception {
		UUID uuid = UUID.randomUUID();
		Files.writeString(temporary.resolve("alice.json"), "{\"schemaVersion\":2,\"accountId\":\"alice\","
				+ "\"microsoft\":{\"clientId\":\"client\",\"scope\":\"scope\",\"accessToken\":\"a\",\"refreshToken\":\"r\",\"expiresAt\":1000},"
				+ "\"libraries\":{"
				+ "\"javalib\":{\"availability\":\"UNAVAILABLE\",\"reason\":\"Account 'alice' owns no game\"},"
				+ "\"otherjavalib\":{\"availability\":\"AVAILABLE\",\"username\":\"Alice\",\"uuid\":\"" + uuid + "\",\"session\":\"cache\"},"
				+ "\"bedrocklib\":{\"availability\":\"UNKNOWN\"},"
				+ "\"removedlib\":{\"availability\":\"AVAILABLE\",\"username\":\"Gone\",\"uuid\":\"" + uuid + "\"}}}");
		AccountStore store = store();

		StoredAccount account = store.read("alice");
		store.write(account);

		assertEquals(Map.of(JAVA, AccountAccess.available(JAVA, "Alice", uuid), BEDROCK, AccountAccess.unknown(BEDROCK, null)),
				account.getEditions());
		assertEquals("cache", account.cachedSession("otherjavalib"));
		assertTrue(Files.readString(temporary.resolve("alice.json")).startsWith("{\"schemaVersion\":3,"));
		assertEquals(account, store.read("alice"));
	}

	@Test
	void readsAnAccountStoredForOneLibraryIntoTheMicrosoftAccountWithoutANewSignIn() throws Exception {
		try (InputStream legacy = getClass().getResourceAsStream("legacy.json")) {
			Files.write(temporary.resolve("legacy.json"), legacy.readAllBytes());
		}
		AccountStore store = store();

		StoredAccount account = store.read("legacy");

		assertEquals("00000000402b5328", account.getToken().getClientId());
		assertEquals("service::user.auth.xboxlive.com::MBI_SSL", account.getToken().getScope());
		assertEquals("fixture-msa-access-token", account.getToken().getAccessToken());
		assertEquals("fixture-msa-refresh-token", account.getToken().getRefreshToken());
		assertEquals(Instant.ofEpochMilli(32_503_680_000_000L), account.getToken().getExpiresAt());
		assertEquals(AccountAccess.available(JAVA, "Fixture", UUID.fromString("5f8eb73b-25be-4c5a-a50f-d27d65e30ca0")), account.access(JAVA));
		assertTrue(account.cachedSession("mcprotocol").contains("\"minecraftProfile\""));

		store.write(account);

		assertTrue(Files.readString(temporary.resolve("legacy.json")).startsWith("{\"schemaVersion\":3,"));
		assertEquals(account, store.read("legacy"));
	}

	@Test
	void refusesAnAccountStoredForOneLibraryThatHoldsNoMicrosoftTokenWithoutQuotingIt() throws Exception {
		Files.writeString(temporary.resolve("old.json"), "{\"schemaVersion\":1,\"accountId\":\"old\",\"provider\":\"javalib\","
				+ "\"credentials\":{\"refresh\":\"secret\"}}");

		var failure = assertThrows(IllegalStateException.class, () -> store().read("old"));

		assertEquals("Stored account 'old' holds no Microsoft token; sign it in again", failure.getMessage());
	}

	@Test
	void namesTheAccountAndDirectoryForMissingAccountsAndReportsAnUnknownSchema() throws IOException {
		Files.writeString(temporary.resolve("future.json"), "{\"schemaVersion\":4}");
		AccountStore store = store();

		var missing = assertThrows(IllegalStateException.class, () -> store.read("missing"));
		var future = assertThrows(IllegalStateException.class, () -> store.read("future"));

		assertEquals("No stored account 'missing' in " + temporary.toAbsolutePath().normalize(), missing.getMessage());
		assertEquals("Stored account 'future' uses schema version 4; this Anvil version reads versions 1 to 3", future.getMessage());
	}

	@Test
	void reportsMalformedFilesWithoutQuotingThem() throws IOException {
		Files.writeString(temporary.resolve("broken.json"), "{\"secret\":");

		var failure = assertThrows(IllegalStateException.class, () -> store().read("broken"));

		assertEquals("Stored account 'broken' is not a valid JSON object", failure.getMessage());
	}

	/**
	 * A store whose libraries {@code javalib}, {@code otherjavalib} and {@code mcprotocol} speak the Java edition and
	 * {@code bedrocklib} the Bedrock edition.
	 */
	private AccountStore store() {
		return new AccountStore(temporary, libraryId -> switch (libraryId) {
			case "javalib", "otherjavalib", "mcprotocol" -> Optional.of(JAVA);
			case "bedrocklib" -> Optional.of(BEDROCK);
			default -> Optional.empty();
		});
	}
}
