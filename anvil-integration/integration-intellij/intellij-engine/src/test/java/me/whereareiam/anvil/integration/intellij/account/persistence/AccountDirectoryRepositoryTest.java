package me.whereareiam.anvil.integration.intellij.account.persistence;

import java.nio.file.Files;
import java.nio.file.Path;

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

	private String account(String id, String user) {
		return "{\"schemaVersion\":1,\"accountId\":\"" + id + "\",\"provider\":\"mcprotocol\",\"username\":\""
				+ user + "\",\"credentials\":{\"test\":\"fixture-secret\"}}";
	}
}
