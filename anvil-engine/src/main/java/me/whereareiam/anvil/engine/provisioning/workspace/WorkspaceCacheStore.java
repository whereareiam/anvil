package me.whereareiam.anvil.engine.provisioning.workspace;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Stores workspace cache paths in Anvil's global cache directory.
 *
 * <p>Each cache declaration gets an isolated content snapshot. Cache entries are never restored
 * outside the process workspace and are written through a temporary directory before replacing an
 * existing entry.</p>
 */
final class WorkspaceCacheStore {
	private final Path root;
	private final WorkspaceFiles files;

	/**
	 * Creates a cache store below the configured Anvil cache directory.
	 *
	 * @param cacheDirectory configured global cache directory
	 * @param files confined workspace file operations
	 */
	public WorkspaceCacheStore(@NotNull Path cacheDirectory, @NotNull WorkspaceFiles files) {
		this.root = cacheDirectory.toAbsolutePath().normalize().resolve("workspaces");
		this.files = files;
	}

	/**
	 * Restores a cached path into the process workspace when an entry exists.
	 *
	 * @param cache cache declaration
	 * @param workspace process workspace
	 * @param identity process and asset identity
	 */
	public synchronized void restore(
			@NotNull WorkspaceCache cache,
			@NotNull Path workspace,
			@NotNull String identity
	) {
		Path entry = entry(identity, cache);
		Path payload = entry.resolve("payload");
		if (!Files.exists(payload))
			return;
		Path destination = files.resolveRelative(workspace, cache.getPath(), "Cache path");
		if (Files.exists(destination))
			files.deleteAbsolute(destination);
		files.copyAbsolute(payload, destination);
	}

	/**
	 * Saves the current workspace path as a cache snapshot.
	 *
	 * @param cache cache declaration
	 * @param workspace process workspace
	 * @param identity process and asset identity
	 */
	public synchronized void save(
			@NotNull WorkspaceCache cache,
			@NotNull Path workspace,
			@NotNull String identity
	) {
		Path source = files.resolveRelative(workspace, cache.getPath(), "Cache path");
		if (!Files.exists(source))
			return;

		Path target = entry(identity, cache);
		Path temporary = target.resolveSibling(target.getFileName() + ".part-" + UUID.randomUUID());
		try {
			Files.createDirectories(temporary);
			files.copyAbsolute(source, temporary.resolve("payload"));
			Files.writeString(temporary.resolve("key"), cache.effectiveKey(), StandardCharsets.UTF_8);
			if (Files.exists(target))
				deleteTree(target);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target);
			}
		} catch (IOException e) {
			deleteTreeQuietly(temporary);
			throw new ProvisioningException("Could not save workspace cache '" + cache.effectiveKey() + "'", e);
		}
	}

	private Path entry(String identity, WorkspaceCache cache) {
		return root.resolve(digest(identity + "\n" + cache.effectiveKey()));
	}

	private String digest(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private void deleteTree(Path root) throws IOException {
		if (!Files.exists(root))
			return;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
				Files.deleteIfExists(path);
		}
	}

	private void deleteTreeQuietly(Path root) {
		try {
			deleteTree(root);
		} catch (IOException ignored) {
			// Preserve the original cache failure.
		}
	}

	static String identity(String processIdentity, List<WorkspaceAsset> assets) {
		return processIdentity + "\nassets=" + assetFingerprint(assets);
	}

	private static String assetFingerprint(List<WorkspaceAsset> assets) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (WorkspaceAsset asset : assets) {
				digest.update(asset.getGroup().getBytes(StandardCharsets.UTF_8));
				digest.update(asset.getTarget().toString().getBytes(StandardCharsets.UTF_8));
				digest.update(asset.getMode().name().getBytes(StandardCharsets.UTF_8));
				Path source = asset.getSource().getPath();
				if (source != null)
					fingerprint(source.toAbsolutePath().normalize(), digest);
			}

			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static void fingerprint(Path source, MessageDigest digest) {
		try {
			if (Files.isRegularFile(source)) {
				digest.update(source.toString().getBytes(StandardCharsets.UTF_8));
				try (var input = Files.newInputStream(source)) {
					byte[] buffer = new byte[8192];
					int read;
					while ((read = input.read(buffer)) >= 0)
						digest.update(buffer, 0, read);
				}
				return;
			}
			if (Files.isDirectory(source))
				try (var paths = Files.walk(source)) {
					for (Path path : paths.sorted().toList()) {
						digest.update(source.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
						if (Files.isRegularFile(path))
							fingerprint(path, digest);
					}
				}
		} catch (IOException e) {
			throw new ProvisioningException("Could not fingerprint workspace asset " + source, e);
		}
	}

}
