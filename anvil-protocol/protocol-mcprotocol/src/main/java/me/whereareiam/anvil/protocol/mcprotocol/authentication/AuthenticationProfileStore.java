package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stores opaque profile documents atomically under an owner-private cache directory.
 * Knows nothing about Microsoft authentication or token refresh.
 */
final class AuthenticationProfileStore {
	private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
	);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE
	);

	private final Path directory;

	AuthenticationProfileStore(@NotNull Path cacheDirectory) {
		directory = cacheDirectory.toAbsolutePath().normalize().resolve("auth");
	}

	@NotNull String read(@NotNull String profileName) throws IOException {
		Path file = profileFile(profileName);
		if (!Files.isRegularFile(file))
			throw new IllegalStateException("Authentication profile '" + profileName
					+ "' does not exist. Run anvilLogin --auth-profile=" + profileName);

		return Files.readString(file, StandardCharsets.UTF_8);
	}

	boolean delete(@NotNull String profileName) throws IOException {
		return Files.deleteIfExists(profileFile(profileName));
	}

	@NotNull Path profileFile(@Nullable String profileName) {
		validateName(profileName);
		return directory.resolve(profileName + ".json");
	}

	void validateName(@Nullable String profileName) {
		if (profileName == null || !PROFILE_NAME.matcher(profileName).matches()
				|| profileName.equals(".") || profileName.equals(".."))
			throw new IllegalArgumentException("Authentication profile must match " + PROFILE_NAME.pattern());
	}

	void write(@NotNull String profileName, @NotNull String json) throws IOException {
		Path target = profileFile(profileName);
		Files.createDirectories(directory);
		applyPermissions(directory, DIRECTORY_PERMISSIONS);
		Path temporary = Files.createTempFile(directory, profileName + "-", ".tmp");
		try {
			applyPermissions(temporary, FILE_PERMISSIONS);
			Files.writeString(temporary, json, StandardCharsets.UTF_8);

			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}

			applyPermissions(target, FILE_PERMISSIONS);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private void applyPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		if (Files.getFileStore(path).supportsFileAttributeView("posix"))
			Files.setPosixFilePermissions(path, permissions);
	}
}
