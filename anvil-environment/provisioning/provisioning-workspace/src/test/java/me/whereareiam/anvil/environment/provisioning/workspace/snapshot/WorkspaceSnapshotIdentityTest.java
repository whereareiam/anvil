package me.whereareiam.anvil.environment.provisioning.workspace.snapshot;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSnapshotIdentityTest {
	private static final String EMPTY_ASSETS = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

	@TempDir
	Path temporary;

	private WorkspaceSnapshotCache snapshots;
	private MinecraftServer server;

	@BeforeEach
	void prepare() {
		snapshots = new WorkspaceSnapshotCache(temporary.resolve("cache"), new TestWorkspaceSnapshotStore(new FileCache(temporary.resolve("cache"))));
		server = MinecraftServer.builder().name("lobby").platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132")).build();
	}
	@Test
	void restoresAnEntryWrittenWithTheLegacyProcessAndAssetEncoding() throws Exception {
		Path entry = temporary.resolve("cache/workspaces/7b18d31c3f324b9b8601b08e6bdbec14e741d1c6b16df011829cf7379613df6b");
		Files.createDirectories(entry.resolve("payload"));
		Files.writeString(entry.resolve("payload/library.jar"), "existing snapshot");
		Files.writeString(entry.resolve("key"), "libraries");

		String identity = snapshots.identity(server, List.of());
		assertEquals("lobby|paper|1.21.11|132||\nassets=" + EMPTY_ASSETS, identity);
		Path restored = temporary.resolve("restored");
		assertTrue(snapshots.restore(identity, "libraries", restored));
		assertEquals("existing snapshot", Files.readString(restored.resolve("library.jar")));
	}
	@Test
	void preservesTheLocalExecutableSelectorWithoutChangingItsPathEncoding() {
		MinecraftServer local = server.toBuilder()
				.distribution(Distribution.local(Path.of("fixtures/server.jar"))).build();

		assertEquals("lobby|paper||||fixtures/server.jar\nassets=" + EMPTY_ASSETS,
				snapshots.identity(local, List.of()));
		assertNotEquals(snapshots.identity(local, List.of()), snapshots.identity(local.toBuilder()
				.distribution(Distribution.local(Path.of("fixtures/other.jar"))).build(), List.of()));
	}
	@Test
	void equivalentResolvedInputsReuseAnIdentity() throws Exception {
		Path source = Files.writeString(temporary.resolve("plugin.jar"), "plugin");
		WorkspaceAsset first = asset(source, "plugins/plugin.jar");
		WorkspaceAsset equivalent = first.toBuilder()
				.source(AssetSource.path(temporary.resolve("unused/../plugin.jar"))).build();
		String identity = snapshots.identity(server, List.of(first));

		assertEquals(identity, snapshots.identity(server.toBuilder().build(), List.of(equivalent)));
		assertEquals(identity, snapshots.identity(server.toBuilder().memoryMegabytes(2048)
				.setting("view-distance", "4").build(), List.of(first)));
	}
	@Test
	void changingProcessSelectorsDoesNotRestoreAnIncompatibleEntry() throws Exception {
		String original = snapshots.identity(server, List.of());
		Path source = Files.writeString(temporary.resolve("payload"), "cached");
		snapshots.save(original, "libraries", source);
		List<MinecraftServer> alternatives = List.of(
				server.toBuilder().name("other").build(),
				server.toBuilder().platform("other-platform").build(),
				server.toBuilder().distribution(Distribution.remote("26.1.2", "132")).build(),
				server.toBuilder().distribution(Distribution.remote("1.21.11", "133")).build()
		);

		for (MinecraftServer alternative : alternatives) {
			String identity = snapshots.identity(alternative, List.of());
			assertNotEquals(original, identity);
			assertFalse(snapshots.restore(identity, "libraries", temporary.resolve("restored")));
		}

		MinecraftServer pinned = server.toBuilder().platform(Platforms.SPIGOT)
				.distribution(Distribution.pinned("1.21.11", "a".repeat(64))).build();
		assertNotEquals(snapshots.identity(pinned, List.of()), snapshots.identity(pinned.toBuilder()
				.distribution(Distribution.pinned("1.21.11", "b".repeat(64))).build(), List.of()));
	}
	@Test
	void assetMetadataAndSourceLocationsSelectDistinctSnapshots() throws Exception {
		Path source = Files.writeString(temporary.resolve("plugin.jar"), "same contents");
		Path otherSource = Files.writeString(temporary.resolve("other-plugin.jar"), "same contents");
		WorkspaceAsset original = asset(source, "plugins/plugin.jar");
		String identity = snapshots.identity(server, List.of(original));
		List<WorkspaceAsset> alternatives = List.of(
				original.toBuilder().group("other-group").build(),
				original.toBuilder().target(Path.of("plugins/other.jar")).build(),
				original.toBuilder().mode(AssetInstallMode.SEED_ONCE).build(),
				original.toBuilder().source(AssetSource.path(otherSource)).build()
		);

		for (WorkspaceAsset alternative : alternatives)
			assertNotEquals(identity, snapshots.identity(server, List.of(alternative)));
	}
	@Test
	void changedAssetBytesDoNotReuseThePreviousSnapshot() throws Exception {
		Path source = Files.writeString(temporary.resolve("plugin.jar"), "first build");
		WorkspaceAsset asset = asset(source, "plugins/plugin.jar");
		String previous = snapshots.identity(server, List.of(asset));
		snapshots.save(previous, "libraries", Files.writeString(temporary.resolve("payload"), "old libraries"));

		Files.writeString(source, "second build");
		String replacement = snapshots.identity(server, List.of(asset));
		assertNotEquals(previous, replacement);
		Path destination = temporary.resolve("restored");
		assertFalse(snapshots.restore(replacement, "libraries", destination));
		assertTrue(snapshots.restore(previous, "libraries", destination));
		assertEquals("old libraries", Files.readString(destination));
	}
	@Test
	void directoryNamesContentsAndEmptyDirectoriesParticipateInIdentity() throws Exception {
		Path source = Files.createDirectory(temporary.resolve("fixtures"));
		Files.createDirectories(source.resolve("nested"));
		Path configuration = Files.writeString(source.resolve("nested/config.yml"), "first");
		WorkspaceAsset asset = asset(source, "plugins/configuration");
		String first = snapshots.identity(server, List.of(asset));

		Files.writeString(configuration, "second");
		String second = snapshots.identity(server, List.of(asset));
		assertNotEquals(first, second);

		Files.move(configuration, source.resolve("nested/renamed.yml"));
		String renamed = snapshots.identity(server, List.of(asset));
		assertNotEquals(second, renamed);

		Files.createDirectory(source.resolve("empty"));
		assertNotEquals(renamed, snapshots.identity(server, List.of(asset)));
	}
	@Test
	void preservesAssetDeclarationOrderInTheIdentity() throws Exception {
		WorkspaceAsset first = asset(Files.writeString(temporary.resolve("first"), "one"), "plugins/one");
		WorkspaceAsset second = asset(Files.writeString(temporary.resolve("second"), "two"), "plugins/two");

		assertNotEquals(snapshots.identity(server, List.of(first, second)),
				snapshots.identity(server, List.of(second, first)));
	}

	private WorkspaceAsset asset(Path source, String target) {
		return WorkspaceAsset.builder().group("fixture").source(AssetSource.path(source))
				.target(Path.of(target)).build();
	}
}
