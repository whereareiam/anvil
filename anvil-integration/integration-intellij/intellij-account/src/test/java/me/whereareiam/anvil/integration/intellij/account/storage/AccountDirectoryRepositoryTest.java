package me.whereareiam.anvil.integration.intellij.account.storage;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.AccountAvailability;
import me.whereareiam.anvil.integration.intellij.account.AccountSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class AccountDirectoryRepositoryTest {
	@TempDir Path directory;

	@Test void preservesSourcesAndExportsTheSelectedFile() throws Exception {
		Path project = Files.createDirectory(directory.resolve("project"));
		Path global = Files.createDirectory(directory.resolve("global"));
		Files.writeString(project.resolve("shared.json"), account("shared", "ProjectUser"));
		Files.writeString(global.resolve("shared.json"), account("shared", "GlobalUser"));
		var repository = new AccountDirectoryRepository();
		var projectListing = repository.readAccounts(project, AccountSource.PROJECT);
		var globalListing = repository.readAccounts(global, AccountSource.GLOBAL);
		assertEquals(1, projectListing.accounts().size());
		assertEquals(1, globalListing.accounts().size());
		assertTrue(projectListing.problems().isEmpty());
		var selected = globalListing.accounts().getFirst();
		Path exported = directory.resolve("export.json");
		repository.exportAccount(selected, exported);
		assertEquals(Files.readString(global.resolve("shared.json")), Files.readString(exported));
		assertThrows(java.io.IOException.class, () -> repository.removeAccount(selected));
		assertTrue(Files.exists(global.resolve("shared.json")));
	}

	@Test void importsOnlyToTheRequestedProjectAndReportsMalformedFilesWithoutCredentials() throws Exception {
		Path project = directory.resolve("project");
		Path file = Files.writeString(directory.resolve("download.json"), account("player", "Player"));
		var repository = new AccountDirectoryRepository();
		repository.importAccount(file, project);
		assertTrue(Files.isRegularFile(project.resolve("player.json")));
		Files.writeString(project.resolve("broken.json"), "{\"credentials\":\"fixture-secret\",oops");
		var listing = repository.readAccounts(project, AccountSource.PROJECT);
		assertEquals(1, listing.accounts().size());
		assertEquals(AccountSource.PROJECT, listing.accounts().getFirst().getSource());
		assertFalse(String.join("", listing.problems()).contains("fixture-secret"));
		assertFalse(listing.problems().isEmpty());
		assertEquals(1, repository.readAccounts(project, AccountSource.PROJECT).accounts().size());
	}

	@Test void readsWhatWasRecordedForEachEditionWithoutAnyLibrary() throws Exception {
		Path current = Files.writeString(directory.resolve("alice.json"), "{\"schemaVersion\":3,\"accountId\":\"alice\","
				+ "\"microsoft\":{\"accessToken\":\"fixture-secret\"},\"editions\":{"
				+ "\"java\":{\"availability\":\"AVAILABLE\",\"username\":\"Alice\",\"uuid\":\"5f8eb73b-25be-4c5a-a50f-d27d65e30ca0\"},"
				+ "\"bedrock\":{\"availability\":\"UNAVAILABLE\",\"reason\":\"Account 'alice' owns no game\"}},"
				+ "\"sessions\":{\"mcprotocol\":\"fixture-secret\"}}");
		var repository = new AccountDirectoryRepository();

		var alice = repository.readMetadata(current);

		assertEquals(AccountAvailability.AVAILABLE, alice.access(MinecraftEdition.of("java")).getAvailability());
		assertEquals("Alice", alice.access(MinecraftEdition.of("java")).getUsername());
		assertEquals("Account 'alice' owns no game", alice.access(MinecraftEdition.of("bedrock")).getReason());
		assertEquals(AccountAvailability.UNKNOWN, alice.access(MinecraftEdition.of("other")).getAvailability());
	}

	@Test void namesAccountsOfSchemasThatRecordedLibrariesWhileTheirEditionsStayUnchecked() throws Exception {
		Path accounts = Files.createDirectory(directory.resolve("accounts"));
		copyFixture("alice.json", accounts);
		copyFixture("bob.json", accounts);
		var repository = new AccountDirectoryRepository();

		var listing = repository.readAccounts(accounts, AccountSource.GLOBAL);

		assertEquals(List.of(), listing.problems());
		var alice = listing.accounts().get(0);
		var bob = listing.accounts().get(1);
		assertEquals(List.of(), alice.getAccount().getAccess());
		assertEquals(List.of(new PlayerProfile("Alice", UUID.fromString("5f8eb73b-25be-4c5a-a50f-d27d65e30ca0"))),
				alice.getLibraryProfiles());
		assertEquals(List.of(), bob.getAccount().getAccess());
		assertEquals(List.of(
				new PlayerProfile("Bob", UUID.fromString("0f4d8a1e-3c5b-4f7a-9e2d-6b1c8a7f3e90")),
				new PlayerProfile("Bob Gamertag", UUID.fromString("7c2e9b44-1a6d-3f08-b5e1-4d9a2c6f8b13"))
		), bob.getLibraryProfiles());
	}

	@Test void recordsNoLibraryProfilesForFilesThatRecordEditions() throws Exception {
		Path accounts = Files.createDirectory(directory.resolve("accounts"));
		Files.writeString(accounts.resolve("carol.json"), "{\"schemaVersion\":3,\"accountId\":\"carol\","
				+ "\"microsoft\":{\"accessToken\":\"fixture-secret\"},\"editions\":{"
				+ "\"java\":{\"availability\":\"AVAILABLE\",\"username\":\"Carol\",\"uuid\":\"5f8eb73b-25be-4c5a-a50f-d27d65e30ca0\"}}}");

		var carol = new AccountDirectoryRepository().readAccounts(accounts, AccountSource.PROJECT).accounts().getFirst();

		assertEquals(List.of(), carol.getLibraryProfiles());
		assertEquals("Carol", carol.getAccount().access(MinecraftEdition.of("java")).getUsername());
	}

	private void copyFixture(String name, Path target) throws Exception {
		try (InputStream fixture = getClass().getResourceAsStream(name)) {
			Files.copy(fixture, target.resolve(name));
		}
	}

	private String account(String id, String user) {
		return "{\"schemaVersion\":1,\"accountId\":\"" + id + "\",\"provider\":\"mcprotocol\",\"username\":\""
				+ user + "\",\"credentials\":{\"test\":\"fixture-secret\"}}";
	}
}
