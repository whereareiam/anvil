package me.whereareiam.anvil.integration.intellij.account.persistence;

import com.intellij.openapi.util.Disposer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;

public class ConfiguredAccountLibraryPlatformTest extends EnginePlatformTestCase {
	public void testPoolsCanBeRenamedWithoutLosingOtherPools() throws Exception {
		var library = ConfiguredAccountLibrary.getInstance(getProject());
		var previous = library.getState();
		try {
			library.setDirectory(temporary("pools"));
			library.savePool(null, "testers", List.of("alice", "bob"));
			library.savePool(null, "admins", List.of("alice"));
			library.savePool("testers", "players", List.of("bob"));
			assertEquals(List.of("bob"), library.catalog().getPools().get("players"));
			assertEquals(List.of("alice"), library.catalog().getPools().get("admins"));
			assertFalse(library.catalog().getPools().containsKey("testers"));
			assertThrows(java.io.IOException.class, () -> library.savePool(null, "players", List.of("alice")));
			library.removePool("players");
			assertEquals(1, library.catalog().getPools().size());
		} finally { library.loadState(previous); }
	}

	public void testDefaultDirectoryIsOutsideTheProjectAndApplyingItClearsTheOverride() throws Exception {
		var library = ConfiguredAccountLibrary.getInstance(getProject());
		var previous = library.getState();
		try {
			Path projectRoot = Path.of(getProject().getBasePath()).toAbsolutePath().normalize();
			Path defaults = library.defaultDirectory();
			assertFalse(defaults.toAbsolutePath().normalize().startsWith(projectRoot));
			assertTrue(defaults.toString().contains(getProject().getLocationHash()));

			library.setDirectory(temporary("custom-accounts"));
			assertFalse(library.getState().getDirectory().isBlank());

			library.setDirectory(defaults);
			assertEquals("", library.getState().getDirectory());
			assertEquals(defaults, library.directory());
		} finally { library.loadState(previous); }
	}

	public void testCatalogReportsDuplicateIdsAcrossConfiguredSources() throws Exception {
		var library = ConfiguredAccountLibrary.getInstance(getProject());
		var preferences = PersistentPreferences.getInstance();
		var previousLibrary = library.getState();
		var previousPreferences = preferences.getState();
		Path project = temporary("project-accounts");
		Path global = temporary("global-accounts");
		try {
			library.setDirectory(project);
			library.setIncludesGlobal(true);
			preferences.setAccountsDirectory(global);
			Files.writeString(project.resolve("shared.json"), account("shared", "Project"));
			Files.writeString(global.resolve("shared.json"), account("shared", "Global"));

			var catalog = library.catalog();
			assertEquals(2, catalog.getAccounts().size());
			assertTrue(catalog.getProblems().stream().anyMatch(problem -> problem.contains("Duplicate account ID")));
		} finally {
			library.loadState(previousLibrary);
			preferences.loadState(previousPreferences);
		}
	}

	private Path temporary(String prefix) throws Exception {
		Path path = Files.createTempDirectory(prefix);
		Disposer.register(getTestRootDisposable(), () -> com.intellij.openapi.util.io.FileUtil.delete(path.toFile()));
		return path;
	}

	private String account(String id, String user) {
		return "{\"schemaVersion\":1,\"accountId\":\""
				+ id
				+ "\",\"provider\":\"mcprotocol\",\"username\":\""
				+ user
				+ "\",\"credentials\":{}}";
	}

}
