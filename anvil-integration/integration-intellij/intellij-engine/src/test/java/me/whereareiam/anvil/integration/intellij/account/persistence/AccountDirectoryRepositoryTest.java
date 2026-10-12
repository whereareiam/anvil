package me.whereareiam.anvil.integration.intellij.account.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.type.AccountAvailability;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;
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

	@Test void showsAccountsOfSchemasThatRecordedLibrariesAsNotYetChecked() throws Exception {
		Path perLibrary = Files.writeString(directory.resolve("alice.json"), "{\"schemaVersion\":2,\"accountId\":\"alice\","
				+ "\"microsoft\":{\"accessToken\":\"fixture-secret\"},\"libraries\":{"
				+ "\"mcprotocol\":{\"availability\":\"AVAILABLE\",\"username\":\"Alice\","
				+ "\"uuid\":\"5f8eb73b-25be-4c5a-a50f-d27d65e30ca0\",\"session\":\"fixture-secret\"}}}");
		Path legacy = Files.writeString(directory.resolve("bob.json"), account("bob", "Bob"));
		var repository = new AccountDirectoryRepository();

		assertEquals(List.of(), repository.readMetadata(perLibrary).getAccess());
		assertEquals(List.of(), repository.readMetadata(legacy).getAccess());
	}

	private String account(String id, String user) {
		return "{\"schemaVersion\":1,\"accountId\":\"" + id + "\",\"provider\":\"mcprotocol\",\"username\":\""
				+ user + "\",\"credentials\":{\"test\":\"fixture-secret\"}}";
	}
}
