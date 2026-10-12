package me.whereareiam.anvil.integration.intellij.account.storage;

import com.intellij.openapi.util.Disposer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import me.whereareiam.anvil.integration.intellij.AccountPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.settings.AccountSettings;

public class StagedAccountDirectoryPlatformTest extends AccountPlatformTestCase {
	public void testProjectAndGlobalPoolFilesMergeWithoutModifyingEitherStore() throws Exception {
		var settings = AccountSettings.getInstance();
		var previous = settings.getState();
		var accounts = AccountLibrary.getInstance(getProject());
		var previousProject = accounts.getState();
		Path project = temporary("project-pools");
		Path global = temporary("global-pools");
		try {
			accounts.setDirectory(project);
			accounts.setIncludesGlobal(true);
			settings.setDirectory(global);
			accounts.savePool(null, "local", List.of("alice"));
			Files.writeString(global.resolve(AccountPoolRepository.FILE_NAME), "schemaVersion=1\npool.shared=bob\n");
			Path materialized;
			try (StagedAccountDirectory workspace = StagedAccountDirectory.create(accounts.roots())) {
				materialized = workspace.directory();
				assertEquals(Map.of("local", List.of("alice"), "shared", List.of("bob")), new AccountPoolRepository(materialized).read());
				assertEquals(Map.of("local", List.of("alice")), accounts.catalog().getPools());
				assertEquals("schemaVersion=1\npool.shared=bob\n", Files.readString(global.resolve(AccountPoolRepository.FILE_NAME)));
			}
			assertFalse(Files.exists(materialized));
			Files.writeString(global.resolve(AccountPoolRepository.FILE_NAME), "schemaVersion=1\npool.local=bob\n");
			assertThrows(IOException.class, () -> StagedAccountDirectory.create(accounts.roots()));
		} finally { accounts.loadState(previousProject); settings.loadState(previous); }
	}
	public void testWorkspaceOwnsPrivateCopiesAndReleasesThemWithoutChangingSources() throws Exception {
		var accounts = AccountLibrary.getInstance(getProject());
		var preferences = AccountSettings.getInstance();
		Path source = temporary("account-source");
		accounts.setDirectory(source);
		accounts.setIncludesGlobal(false);
		Files.writeString(source.resolve("alice.json"), "opaque credential fixture");
		Path copied;
		try (StagedAccountDirectory workspace = StagedAccountDirectory.create(accounts.roots())) {
			copied = workspace.directory();
			assertEquals("opaque credential fixture", Files.readString(copied.resolve("alice.json")));
			if (Files.getFileStore(copied).supportsFileAttributeView("posix"))
				assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
						Files.getPosixFilePermissions(copied.resolve("alice.json")));
		}
		assertFalse(Files.exists(copied));
		assertEquals("opaque credential fixture", Files.readString(source.resolve("alice.json")));
	}

	public void testPoolFilesWithoutTheCurrentSchemaOrWithDuplicateAccountsAreRejected() throws Exception {
		Path directory = temporary("pool-format");
		AccountPoolRepository pools = new AccountPoolRepository(directory);

		Files.writeString(directory.resolve(AccountPoolRepository.FILE_NAME), "testers=alice\n");
		assertThrows(IOException.class, pools::read);

		Files.writeString(directory.resolve(AccountPoolRepository.FILE_NAME), "schemaVersion=1\npool.testers=alice,alice\n");
		assertThrows(IOException.class, pools::read);

		pools.replace(Map.of("testers", List.of("bob", "alice")));
		assertEquals(Map.of("testers", List.of("bob", "alice")), pools.read());
	}

	private Path temporary(String prefix) throws Exception {
		Path path = Files.createTempDirectory(prefix);
		Disposer.register(getTestRootDisposable(), () -> com.intellij.openapi.util.io.FileUtil.delete(path.toFile()));
		return path;
	}

}
