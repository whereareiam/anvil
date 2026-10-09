package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores one library-owned authenticated account per local JSON file; its "provider" key names the library.
 */
final class AuthenticationAccountStore {
	private static final int SCHEMA_VERSION = 1;
	private static final Pattern ACCOUNT_ID = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE
	);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE
	);

	private final Path directory;

	AuthenticationAccountStore(@NotNull Path accountsDirectory) {
		directory = accountsDirectory.toAbsolutePath().normalize();
	}

	@NotNull String credentials(@NotNull String accountId) throws IOException {
		JsonObject document = parse(accountId);
		if (!document.has("credentials")
				|| !document.get("credentials").isJsonObject())
			throw new IllegalStateException("Account '" + accountId + "' has no provider credentials");

		return document.getAsJsonObject("credentials").toString();
	}

	@NotNull List<AuthenticationAccount> accounts(@NotNull String libraryId) throws IOException {
		if (!Files.isDirectory(directory)) return List.of();

		List<AuthenticationAccount> accounts = new ArrayList<>();
		try (var files = Files.list(directory)) {
			List<Path> accountFiles = files
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList();
			for (Path file : accountFiles) {
				String fileName = file.getFileName().toString();
				String accountId = fileName.substring(0, fileName.length() - ".json".length());
				try {
					JsonObject document = parse(accountId);
					if (!libraryId.equals(document.get("provider").getAsString())) continue;
					if (!document.has("credentials") || !document.get("credentials").isJsonObject()) continue;

					accounts.add(new AuthenticationAccount(
							accountId,
							libraryId,
							document.has("username") ? document.get("username").getAsString() : null,
							document.has("uuid") ? UUID.fromString(document.get("uuid").getAsString()) : null
					));
				} catch (RuntimeException ignored) {
					// Invalid files remain visible to import/repair tooling through their filename.
				}
			}
		}

		return accounts.stream()
				.sorted(Comparator.comparing(AuthenticationAccount::getAccountId))
				.toList();
	}

	void write(
			@NotNull String accountId,
			@NotNull String libraryId,
			@NotNull String username,
			@NotNull UUID uuid,
			@NotNull String credentials
	) throws IOException {
		validateName(accountId);

		JsonObject document = new JsonObject();
		document.addProperty("schemaVersion", SCHEMA_VERSION);
		document.addProperty("accountId", accountId);
		document.addProperty("provider", libraryId);
		document.addProperty("username", username);
		document.addProperty("uuid", uuid.toString());
		document.add("credentials", JsonParser.parseString(credentials));

		Files.createDirectories(directory);
		applyPermissions(directory, DIRECTORY_PERMISSIONS);

		Path temporary = Files.createTempFile(directory, accountId + "-", ".tmp");
		Path target = accountFile(accountId);
		try {
			applyPermissions(temporary, FILE_PERMISSIONS);
			Files.writeString(temporary, document.toString(), StandardCharsets.UTF_8);
			try {
				Files.move(
						temporary,
						target,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING
				);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}

			applyPermissions(target, FILE_PERMISSIONS);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	boolean delete(@NotNull String accountId) throws IOException {
		return Files.deleteIfExists(accountFile(accountId));
	}

	@NotNull Path accountFile(@Nullable String accountId) {
		validateName(accountId);
		return directory.resolve(accountId + ".json");
	}

	private JsonObject parse(@NotNull String accountId) throws IOException {
		Path file = accountFile(accountId);
		if (!Files.isRegularFile(file))
			throw new IllegalStateException("No stored account '" + accountId + "' in " + directory);

		JsonObject document = read(accountId, file);
		JsonElement version = document.get("schemaVersion");
		if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber())
			throw new IllegalStateException("Stored account '" + accountId + "' has no schema version");

		if (version.getAsInt() != SCHEMA_VERSION) {
			throw new IllegalStateException(
					"Stored account '" + accountId + "' uses schema version " + version.getAsInt()
							+ "; this Anvil version reads version " + SCHEMA_VERSION
			);
		}

		return document;
	}

	/**
	 * Parses the account document. Parser messages can quote file content, which holds tokens, so
	 * malformed JSON is reported without them.
	 */
	private JsonObject read(@NotNull String accountId, @NotNull Path file) throws IOException {
		String content = Files.readString(file, StandardCharsets.UTF_8);
		try {
			return JsonParser.parseString(content).getAsJsonObject();
		} catch (RuntimeException malformed) {
			throw new IllegalStateException("Stored account '" + accountId + "' is not a valid JSON object");
		}
	}

	private void validateName(@Nullable String accountId) {
		if (accountId == null
				|| !ACCOUNT_ID.matcher(accountId).matches()
				|| accountId.equals(".")
				|| accountId.equals("..")) {
			throw new IllegalArgumentException("Account ID must match " + ACCOUNT_ID.pattern());
		}
	}

	private void applyPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		if (Files.getFileStore(path).supportsFileAttributeView("posix"))
			Files.setPosixFilePermissions(path, permissions);
	}
}
