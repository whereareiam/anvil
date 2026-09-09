package me.whereareiam.anvil.environment.cache.filesystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds a real operating-system cache entry lock while its parent tests another JVM's access.
 */
public final class CacheEntryLockChild {
	public static void main(String[] arguments) throws Exception {
		var cache = new FileCache(Path.of(arguments[0]));
		try (var entry = cache.open(Path.of(arguments[1]));
		     var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			Files.createDirectories(entry.path().getParent());
			Files.writeString(entry.path(), "incomplete");
			System.out.println("READY");
			System.out.flush();
			if (!"RELEASE".equals(input.readLine())) throw new IllegalStateException("Expected release command");
			try (var write = entry.stageFile()) {
				Files.writeString(write.path(), "complete");
				write.commit();
			}
		}
	}
}
