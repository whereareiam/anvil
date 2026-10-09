package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationAccountStoreTest {
	@TempDir
	Path temporary;

	@Test
	void writesAccountsWithOwnerOnlyPermissions() throws Exception {
		AuthenticationAccountStore store = new AuthenticationAccountStore(temporary);
		store.write("developer", "mcprotocol", "Developer", UUID.randomUUID(), "{\"refresh\":\"secret\"}");
		Path account = store.accountFile("developer");

		if (Files.getFileStore(account).supportsFileAttributeView("posix")) {
			assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
					Files.getPosixFilePermissions(account));
		}
		assertEquals("{\"refresh\":\"secret\"}", store.credentials("developer"));
		assertEquals(true, store.delete("developer"));
		assertEquals(false, Files.exists(account));
	}

	@Test
	void rejectsPathTraversalAccountNames() {
		AuthenticationAccountStore store = new AuthenticationAccountStore(temporary);
		assertThrows(IllegalArgumentException.class, () -> store.accountFile("../credentials"));
	}

	@Test
	void listsSafeAccountMetadataWithoutCredentials() throws Exception {
		AuthenticationAccountStore store = new AuthenticationAccountStore(temporary);
		var uuid = UUID.randomUUID();
		store.write("developer", "mcprotocol", "Developer", uuid, "{\"refresh\":\"secret\"}");
		var account = store.accounts("mcprotocol").getFirst();
		assertEquals("developer", account.getAccountId());
		assertEquals("Developer", account.getUsername());
		assertEquals(uuid, account.getUniqueId());
	}

	@Test
	void namesTheAccountAndDirectoryForMissingAccountsWithoutFrontendGuidance() {
		var failure = assertThrows(IllegalStateException.class, () -> new AuthenticationAccountStore(temporary).credentials("missing"));
		assertEquals("No stored account 'missing' in " + temporary.toAbsolutePath().normalize(), failure.getMessage());
	}

	@Test
	void reportsAnUnsupportedSchemaInsteadOfCallingTheFileInvalid() throws Exception {
		Files.writeString(temporary.resolve("future.json"), "{\"schemaVersion\":2,\"credentials\":{}}");
		var failure = assertThrows(IllegalStateException.class, () -> new AuthenticationAccountStore(temporary).credentials("future"));
		assertEquals("Stored account 'future' uses schema version 2; this Anvil version reads version 1", failure.getMessage());
	}
}
