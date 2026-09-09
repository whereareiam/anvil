package me.whereareiam.anvil.environment.provisioning.workspace.snapshot;

import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Publishes a deliberately incomplete snapshot while holding a real lease in a separate JVM.
 */
public final class WorkspaceSnapshotLockChild {
	public static void main(String[] arguments) throws Exception {
		Path cacheDirectory = Path.of(arguments[0]);
		Path entry = Path.of(arguments[1]);
		String key = arguments[2];
		String version = arguments[3];
		try (CacheEntry ignored = new FileCache(cacheDirectory).open(entry);
		     var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			Path payload = entry.resolve("payload");
			Files.createDirectories(payload.resolve("nested"));
			Files.writeString(payload.resolve("version.txt"), "incomplete");
			System.out.println("READY");
			System.out.flush();
			if (!"PUBLISH".equals(input.readLine()))
				throw new IllegalStateException("Expected a publication command from the parent JVM");

			Files.writeString(payload.resolve("version.txt"), version);
			Files.writeString(payload.resolve("nested/data.txt"), version.repeat(1_000));
			Files.writeString(entry.resolve("key"), key);
		}
		System.out.println("DONE");
		System.out.flush();
	}
}
