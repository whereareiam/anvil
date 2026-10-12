package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {
	static final WorkerLibrary JAVA = library("javalib", "JavaLib", true);
	static final WorkerLibrary BEDROCK = library("bedrocklib", "BedrockLib", false);

	@TempDir
	Path temporary;

	@Test
	void writesLabelledAccountsWithOwnerOnlyPermissionsAndReadsThemBack() throws Exception {
		AccountStore store = new AccountStore(JAVA, temporary);
		UUID uuid = UUID.randomUUID();
		store.write(record("developer", uuid, "{\"refresh\":\"secret\"}"));
		Path account = store.accountFile("developer");

		if (Files.getFileStore(account).supportsFileAttributeView("posix"))
			assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(account));
		assertTrue(Files.readString(account).contains("\"provider\":\"javalib\""));
		AccountRecord read = store.read("developer");
		assertEquals("{\"refresh\":\"secret\"}", read.getCredentials());
		assertEquals("Developer", read.getUsername());
		assertEquals(uuid, read.getUniqueId());
		assertTrue(store.delete("developer"));
		assertFalse(store.delete("developer"));
		assertFalse(Files.exists(account));
	}

	@Test
	void rejectsPathTraversalAccountNamesAndCredentialsThatAreNoObject() {
		AccountStore store = new AccountStore(JAVA, temporary);

		assertThrows(IllegalArgumentException.class, () -> store.accountFile("../credentials"));
		var credentials = assertThrows(IllegalArgumentException.class, () -> store.write(record("developer", UUID.randomUUID(), "[\"secret\"]")));
		assertFalse(credentials.getMessage().contains("secret"));
	}

	@Test
	void listsOnlyTheLibrarysAccountsWithoutCredentials() throws Exception {
		UUID uuid = UUID.randomUUID();
		new AccountStore(JAVA, temporary).write(record("java", uuid, "{}"));
		new AccountStore(BEDROCK, temporary).write(record("bedrock", UUID.randomUUID(), "{}"));
		Files.writeString(temporary.resolve("corrupt.json"), "{BROKEN");

		List<AuthenticationAccount> accounts = new AccountStore(JAVA, temporary).accounts();

		assertEquals(List.of("java"), accounts.stream().map(AuthenticationAccount::getAccountId).toList());
		assertEquals("javalib", accounts.getFirst().getLibraryId());
		assertEquals("Developer", accounts.getFirst().getUsername());
		assertEquals(uuid, accounts.getFirst().getUniqueId());
		assertEquals(List.of("bedrock"), new AccountStore(BEDROCK, temporary).accounts().stream().map(AuthenticationAccount::getAccountId).toList());
	}

	@Test
	void refusesToReadReplaceOrRemoveAnotherLibrarysAccount() throws Exception {
		new AccountStore(BEDROCK, temporary).write(record("shared", UUID.randomUUID(), "{}"));
		AccountStore java = new AccountStore(JAVA, temporary);

		var read = assertThrows(IllegalStateException.class, () -> java.read("shared"));
		assertEquals("Account 'shared' belongs to protocol library 'bedrocklib', so JavaLib ('javalib') cannot use it; "
				+ "log in or out with 'bedrocklib', or choose another account ID", read.getMessage());
		assertThrows(IllegalStateException.class, () -> java.requireAvailable("shared"));
		assertThrows(IllegalStateException.class, () -> java.delete("shared"));
		assertTrue(Files.exists(java.accountFile("shared")));
		java.requireAvailable("unused");
	}

	@Test
	void givesUnlabelledAccountsToTheLibraryThatClaimsThemAndLabelsThemOnTheNextWrite() throws Exception {
		Files.writeString(temporary.resolve("legacy.json"), """
				{"schemaVersion":1,"accountId":"legacy","username":"Legacy","uuid":"%s","credentials":{"refresh":"secret"}}
				""".formatted(UUID.randomUUID()));
		AccountStore java = new AccountStore(JAVA, temporary);
		AccountStore bedrock = new AccountStore(BEDROCK, temporary);

		assertEquals(List.of("legacy"), java.accounts().stream().map(AuthenticationAccount::getAccountId).toList());
		assertTrue(bedrock.accounts().isEmpty());
		var refused = assertThrows(IllegalStateException.class, () -> bedrock.read("legacy"));
		assertTrue(refused.getMessage().startsWith("Stored account 'legacy' names no protocol library, and BedrockLib does not read"),
				refused.getMessage());

		java.write(java.read("legacy"));

		assertTrue(Files.readString(java.accountFile("legacy")).contains("\"provider\":\"javalib\""));
	}

	@Test
	void namesTheAccountAndDirectoryForMissingAccounts() {
		var failure = assertThrows(IllegalStateException.class, () -> new AccountStore(JAVA, temporary).read("missing"));

		assertEquals("No stored account 'missing' in " + temporary.toAbsolutePath().normalize(), failure.getMessage());
	}

	@Test
	void reportsAnUnsupportedSchemaInsteadOfCallingTheFileInvalid() throws Exception {
		Files.writeString(temporary.resolve("future.json"), "{\"schemaVersion\":2,\"credentials\":{}}");

		var failure = assertThrows(IllegalStateException.class, () -> new AccountStore(JAVA, temporary).read("future"));

		assertEquals("Stored account 'future' uses schema version 2; this Anvil version reads version 1", failure.getMessage());
	}

	@Test
	void doesNotExposeCorruptAccountContentsInFailures() throws Exception {
		Files.writeString(temporary.resolve("corrupt.json"), "{\"token\":\"private-token\",BROKEN");

		var failure = assertThrows(IllegalStateException.class, () -> new AccountStore(JAVA, temporary).read("corrupt"));

		assertEquals("Stored account 'corrupt' is not a valid JSON object", failure.getMessage());
		assertNull(failure.getCause());
	}

	static AccountRecord record(String accountId, UUID uuid, String credentials) {
		return AccountRecord.builder().accountId(accountId).username("Developer").uniqueId(uuid).credentials(credentials).build();
	}

	private static WorkerLibrary library(String id, String name, boolean readsUnlabelled) {
		return WorkerLibrary.builder()
				.id(id)
				.name(name)
				.clientPort(Runnable.class)
				.releases("unused.toml")
				.pinTask("unused")
				.readsUnlabelledAccounts(readsUnlabelled)
				.build();
	}
}
