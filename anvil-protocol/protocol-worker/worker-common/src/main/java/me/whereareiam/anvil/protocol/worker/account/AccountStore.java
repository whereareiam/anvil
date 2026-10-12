package me.whereareiam.anvil.protocol.worker.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The account files of one hosted library: one private JSON file per account ID in the shared accounts directory,
 * whose {@code provider} key names the library that owns it. Every library reads and writes the same directory, so
 * an account ID belongs to one library; the store refuses to read, replace or remove another library's account.
 *
 * <p>A file without a {@code provider} key, written before account files recorded their owner, belongs to the
 * library that {@link WorkerLibrary#isReadsUnlabelledAccounts() claims unlabelled accounts}; writing it again
 * labels it.</p>
 */
final class AccountStore {
	private static final int SCHEMA_VERSION = 1;
	private static final String OWNER = "provider";
	private static final String SUFFIX = ".json";
	private static final Pattern ACCOUNT_ID = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE
	);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE
	);

	private final WorkerLibrary library;
	private final Path directory;

	AccountStore(@NotNull WorkerLibrary library, @NotNull Path accountsDirectory) {
		this.library = library;
		directory = accountsDirectory.toAbsolutePath().normalize();
	}

	/**
	 * Lists the accounts this library owns; files of other libraries and unreadable files are left out.
	 *
	 * @return account metadata ordered by account ID
	 * @throws IOException when the directory cannot be listed
	 */
	@NotNull List<AuthenticationAccount> accounts() throws IOException {
		if (!Files.isDirectory(directory)) return List.of();

		List<Path> files;
		try (Stream<Path> listed = Files.list(directory)) {
			files = listed.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).sorted().toList();
		}

		List<AuthenticationAccount> accounts = new ArrayList<>();
		for (Path file : files) {
			String fileName = file.getFileName().toString();
			String accountId = fileName.substring(0, fileName.length() - SUFFIX.length());
			try {
				ObjectNode document = parse(accountId);
				if (!owns(document) || !document.path("credentials").isObject()) continue;

				accounts.add(new AuthenticationAccount(
						accountId,
						library.getId(),
						document.path("username").isTextual() ? document.get("username").asText() : null,
						document.path("uuid").isTextual() ? UUID.fromString(document.get("uuid").asText()) : null
				));
			} catch (RuntimeException ignored) {
				// Invalid files and other libraries' IDs remain visible to repair tooling through their file name.
			}
		}

		return List.copyOf(accounts);
	}

	/**
	 * Reads an account this library owns.
	 *
	 * @param accountId owner-local account ID
	 * @return the stored record
	 * @throws IllegalStateException when no such account is stored, its file is invalid or uses another schema, or
	 * another library owns it
	 * @throws IOException when the file cannot be read
	 */
	@NotNull AccountRecord read(@NotNull String accountId) throws IOException {
		ObjectNode document = parse(accountId);
		requireOwned(accountId, document);

		JsonNode credentials = document.path("credentials");
		if (!credentials.isObject() || !document.path("username").isTextual() || !document.path("uuid").isTextual())
			throw new IllegalStateException("Stored account '" + accountId + "' has no profile or credentials");

		try {
			return AccountRecord.builder()
					.accountId(accountId)
					.username(document.get("username").asText())
					.uniqueId(UUID.fromString(document.get("uuid").asText()))
					.credentials(JSON.writeValueAsString(credentials))
					.build();
		} catch (IllegalArgumentException invalid) {
			throw new IllegalStateException("Stored account '" + accountId + "' has an invalid profile UUID");
		}
	}

	/**
	 * Makes sure this library may store an account under the ID: no file holds it, or this library owns it.
	 *
	 * @param accountId owner-local account ID
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 * @throws IllegalStateException when another library owns the ID or its file is unreadable
	 * @throws IOException when the file cannot be read
	 */
	void requireAvailable(@NotNull String accountId) throws IOException {
		if (!Files.exists(accountFile(accountId))) return;

		requireOwned(accountId, parse(accountId));
	}

	/**
	 * Stores a record labelled with this library, replacing the file atomically with owner-only permissions.
	 *
	 * @param account record to store
	 * @throws IllegalArgumentException when the credentials are not the text of a JSON object
	 * @throws IOException when the file cannot be written
	 */
	void write(@NotNull AccountRecord account) throws IOException {
		String accountId = account.getAccountId();
		Path target = accountFile(accountId);

		ObjectNode document = JSON.createObjectNode();
		document.put("schemaVersion", SCHEMA_VERSION);
		document.put("accountId", accountId);
		document.put(OWNER, library.getId());
		document.put("username", account.getUsername());
		document.put("uuid", account.getUniqueId().toString());
		document.set("credentials", credentials(account));

		Files.createDirectories(directory);
		applyPermissions(directory, DIRECTORY_PERMISSIONS);

		Path temporary = Files.createTempFile(directory, accountId + "-", ".tmp");
		try {
			applyPermissions(temporary, FILE_PERMISSIONS);
			Files.writeString(temporary, JSON.writeValueAsString(document), StandardCharsets.UTF_8);
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

	/**
	 * Removes an account this library owns.
	 *
	 * @param accountId owner-local account ID
	 * @return whether a file was removed
	 * @throws IllegalStateException when another library owns the ID or its file is unreadable
	 * @throws IOException when the file cannot be read or removed
	 */
	boolean delete(@NotNull String accountId) throws IOException {
		Path file = accountFile(accountId);
		if (!Files.exists(file)) return false;

		requireOwned(accountId, parse(accountId));
		return Files.deleteIfExists(file);
	}

	/**
	 * Returns the file of an account ID.
	 *
	 * @param accountId owner-local account ID
	 * @return file below the accounts directory
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 */
	@NotNull Path accountFile(@Nullable String accountId) {
		if (accountId == null || !ACCOUNT_ID.matcher(accountId).matches() || accountId.equals(".") || accountId.equals(".."))
			throw new IllegalArgumentException("Account ID must match " + ACCOUNT_ID.pattern());

		return directory.resolve(accountId + SUFFIX);
	}

	private boolean owns(ObjectNode document) {
		JsonNode owner = document.get(OWNER);
		if (owner == null || owner.isNull()) return library.isReadsUnlabelledAccounts();

		return owner.isTextual() && owner.asText().equals(library.getId());
	}

	private void requireOwned(String accountId, ObjectNode document) {
		if (owns(document)) return;

		JsonNode owner = document.get(OWNER);
		if (owner == null || owner.isNull())
			throw new IllegalStateException("Stored account '" + accountId + "' names no protocol library, and " + library.getName()
					+ " does not read such accounts; log in again with the library that stored it, or choose another account ID");

		throw new IllegalStateException("Account '" + accountId + "' belongs to protocol library '" + owner.asText() + "', so "
				+ library.getName() + " ('" + library.getId() + "') cannot use it; log in or out with '" + owner.asText()
				+ "', or choose another account ID");
	}

	private ObjectNode parse(String accountId) throws IOException {
		Path file = accountFile(accountId);
		if (!Files.isRegularFile(file))
			throw new IllegalStateException("No stored account '" + accountId + "' in " + directory);

		JsonNode document;
		try {
			document = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
		} catch (JsonProcessingException malformed) {
			// Parser messages can quote file content, which holds tokens, so malformed JSON is reported without them.
			document = null;
		}
		if (!(document instanceof ObjectNode object))
			throw new IllegalStateException("Stored account '" + accountId + "' is not a valid JSON object");

		JsonNode version = object.get("schemaVersion");
		if (version == null || !version.isInt())
			throw new IllegalStateException("Stored account '" + accountId + "' has no schema version");
		if (version.asInt() != SCHEMA_VERSION)
			throw new IllegalStateException("Stored account '" + accountId + "' uses schema version " + version.asInt()
					+ "; this Anvil version reads version " + SCHEMA_VERSION);

		return object;
	}

	private static JsonNode credentials(AccountRecord account) {
		JsonNode credentials;
		try {
			credentials = JSON.readTree(account.getCredentials());
		} catch (JsonProcessingException malformed) {
			credentials = null;
		}
		if (credentials == null || !credentials.isObject())
			throw new IllegalArgumentException("The credentials of account '" + account.getAccountId() + "' are not a JSON object");

		return credentials;
	}

	private static void applyPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		if (Files.getFileStore(path).supportsFileAttributeView("posix"))
			Files.setPosixFilePermissions(path, permissions);
	}
}
