package me.whereareiam.anvil.environment.cache.filesystem;

import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.cache.api.model.CacheKey;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Resolves filesystem cache keys and grants entry access using JVM and operating-system locks.
 */
public final class FileCache implements Cache {
	private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

	private final @NotNull Path root;

	/**
	 * Selects the shared cache root without acquiring resources or creating payload directories.
	 *
	 * @param root shared filesystem cache directory
	 */
	public FileCache(@NotNull Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	@Override
	public @NotNull Path path(@NotNull CacheKey key) {
		Path namespace = Path.of(key.getNamespace());
		if (namespace.isAbsolute() || namespace.normalize().toString().isEmpty())
			throw new IllegalArgumentException("Cache namespace must be a relative directory: " + namespace);
		for (Path part : namespace)
			if (part.toString().equals(".."))
				throw new IllegalArgumentException("Cache namespace must not traverse its root: " + namespace);
		if (key.getSuffix().contains("/") || key.getSuffix().contains("\\"))
			throw new IllegalArgumentException("Cache suffix must not contain path separators: " + key.getSuffix());

		return root.resolve(namespace).resolve(hash(key.getValue()) + key.getSuffix());
	}

	@Override
	public @NotNull CacheEntry open(@NotNull Path entry) {
		Path target = entry.toAbsolutePath().normalize();
		String key = hash(target.toString());
		ReentrantLock threadLock = LOCKS.computeIfAbsent(key, ignored -> new ReentrantLock());
		try {
			threadLock.lockInterruptibly();
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new CacheException("Interrupted acquiring cache entry " + target, failure);
		}

		try {
			Path file = root.resolve("locks").resolve(key + ".lock");
			Files.createDirectories(file.getParent());
			FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

			return acquire(target, channel, threadLock);
		} catch (IOException | RuntimeException | Error failure) {
			threadLock.unlock();
			throw new CacheException("Could not acquire cache entry " + target, failure);
		}
	}

	private CacheEntry acquire(Path path, FileChannel channel, ReentrantLock threadLock) throws IOException {
		try {
			FileLock fileLock = channel.lock();

			return new LockedFileCacheEntry(path, channel, fileLock, threadLock);
		} catch (IOException | RuntimeException | Error failure) {
			try {
				channel.close();
			} catch (IOException cleanup) {
				failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	private static String hash(String input) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));

			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable", failure);
		}
	}
}
