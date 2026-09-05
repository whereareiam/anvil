package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationProfileStoreTest {
	@TempDir
	Path temporary;

	@Test
	void writesProfilesWithOwnerOnlyPermissions() throws Exception {
		AuthenticationProfileStore store = new AuthenticationProfileStore(temporary);
		store.write("developer", "{\"refresh\":\"secret\"}");
		Path profile = store.profileFile("developer");

		if (Files.getFileStore(profile).supportsFileAttributeView("posix")) {
			assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
					Files.getPosixFilePermissions(profile));
		}
		assertEquals("{\"refresh\":\"secret\"}", store.read("developer"));
		assertEquals(true, store.delete("developer"));
		assertEquals(false, Files.exists(profile));
	}

	@Test
	void rejectsPathTraversalProfileNames() {
		AuthenticationProfileStore store = new AuthenticationProfileStore(temporary);
		assertThrows(IllegalArgumentException.class, () -> store.profileFile("../credentials"));
	}

	@Test
	void reportsTheSupportedAuthenticationTaskOptionForMissingProfiles() {
		var failure = assertThrows(IllegalStateException.class, () -> new AuthenticationProfileStore(temporary).read("missing"));
		assertTrue(failure.getMessage().contains("--auth-profile=missing"));
	}
}
