package me.whereareiam.anvil.environment.provisioning.workspace.snapshot;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot.WorkspaceSnapshotStore;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Derives workspace snapshot identities and stores their payloads through shared filesystem storage.
 *
 * <p>The store owns identity derivation, the snapshot format, and source validation. Entry leases
 * and staged writes coordinate publication through the shared storage; the workspace owner controls
 * lifecycle policy and keeps source files stable while they are fingerprinted or copied.</p>
 */
public final class WorkspaceSnapshotCache {
	private final @NotNull Path root;
	private final @NotNull WorkspaceSnapshotStore storage;

	/**
	 * Creates a snapshot store using the shared cache entry and publication services.
	 *
	 * @param root shared snapshot storage root
	 * @param storage coordinated snapshot publication and restoration
	 */
	public WorkspaceSnapshotCache(@NotNull Path root, @NotNull WorkspaceSnapshotStore storage) {
		this.root = root.toAbsolutePath().normalize();
		this.storage = storage;
	}

	/**
	 * Fingerprints the process selection and ordered asset inputs used to select compatible snapshots.
	 *
	 * @param process process declaration
	 * @param assets assets with resolved local sources
	 * @return compatibility identity shared by the process workspace's snapshot entries
	 */
	public @NotNull String identity(@NotNull MinecraftProcess process, @NotNull List<WorkspaceAsset> assets) {
		return WorkspaceSnapshotIdentity.of(process, assets);
	}

	/**
	 * Restores an existing snapshot through coordinated replacement of the destination.
	 * An absent snapshot leaves the destination unchanged.
	 *
	 * @param identity process and asset compatibility identity
	 * @param key declared snapshot key
	 * @param destination confined workspace path to replace
	 * @return whether snapshot content was restored
	 */
	public boolean restore(@NotNull String identity, @NotNull String key, @NotNull Path destination) {
		try {
			return storage.restore(entry(identity, key), destination.toAbsolutePath().normalize(), snapshot -> {
				validateEntry(snapshot);
				Path payload = snapshot.resolve("payload");
				if (Files.notExists(payload, LinkOption.NOFOLLOW_LINKS)) return null;

				return staging -> copy(payload, staging);
			});
		} catch (IOException failure) {
			throw new ProvisioningException("Could not restore workspace snapshot '" + key + "'", failure);
		}
	}

	/**
	 * Publishes a snapshot of a stopped process's selected workspace path.
	 * A missing source or failed copy preserves previously published content.
	 *
	 * @param identity process and asset compatibility identity
	 * @param key declared snapshot key
	 * @param source stable workspace file or directory to retain
	 */
	public void save(@NotNull String identity, @NotNull String key, @NotNull Path source) {
		try {
			storage.save(entry(identity, key), snapshot -> {
				if (Files.notExists(source, LinkOption.NOFOLLOW_LINKS)) return null;
				validateEntry(snapshot);

				return staging -> {
					Files.createDirectories(staging);
					copy(source.toAbsolutePath().normalize(), staging.resolve("payload"));
					Files.writeString(staging.resolve("key"), key, StandardCharsets.UTF_8);
				};
			});
		} catch (IOException failure) {
			throw new ProvisioningException("Could not save workspace snapshot '" + key + "'", failure);
		}
	}

	private @NotNull Path entry(@NotNull String identity, @NotNull String key) {
		return root.resolve("workspaces").resolve(WorkspaceSnapshotIdentity.key(identity, key));
	}

	private void validateEntry(@NotNull Path entry) {
		if (Files.isSymbolicLink(entry.getParent()) || Files.isSymbolicLink(entry))
			throw new ProvisioningException("Workspace snapshot entry must not pass through a symbolic link: " + entry);
	}

	private void copy(@NotNull Path source, @NotNull Path destination) throws IOException {
		BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		requireSupported(source, attributes);
		Files.createDirectories(destination.getParent());
		if (attributes.isRegularFile()) {
			Files.copy(source, destination, LinkOption.NOFOLLOW_LINKS);
			return;
		}

		if (destination.startsWith(source))
			throw new ProvisioningException("Workspace snapshot cannot be copied inside its source: " + source);

		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
				requireSupported(directory, attributes);
				Files.createDirectories(destination.resolve(source.relativize(directory)));

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				requireSupported(file, attributes);
				Files.copy(file, destination.resolve(source.relativize(file)), LinkOption.NOFOLLOW_LINKS);

				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void requireSupported(@NotNull Path source, @NotNull BasicFileAttributes attributes) {
		if (attributes.isSymbolicLink())
			throw new ProvisioningException("Workspace snapshot source must not be a symbolic link: " + source);
		if (!attributes.isRegularFile() && !attributes.isDirectory())
			throw new ProvisioningException("Workspace snapshot source is neither a file nor a directory: " + source);
	}

}
