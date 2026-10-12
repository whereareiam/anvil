package me.whereareiam.anvil.protocol.worker.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.type.AccountAvailability;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The account files of the native worker: one private JSON file per account ID, holding one Microsoft account's
 * token and, for each library that used the account, what it recorded and the session it cached.
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 2,
 *   "accountId": "alice",
 *   "microsoft": { "clientId": "...", "scope": "...", "accessToken": "...", "refreshToken": "...", "expiresAt": 0 },
 *   "libraries": {
 *     "mcprotocol": { "availability": "AVAILABLE", "username": "Alice", "uuid": "...", "session": "..." }
 *   }
 * }
 * }</pre>
 *
 * <p>Files of schema version 1, which Anvil wrote while an account belonged to one library, are read into this
 * shape: their {@code credentials} are the library's saved MinecraftAuth session, whose Microsoft application and
 * token become the account's token, and which stays the cached session of the library their {@code provider} key
 * names. Writing such an account stores it in the current schema.</p>
 */
final class AccountStore {
	private static final int SCHEMA_VERSION = 2;
	private static final int ACCOUNT_PER_LIBRARY_SCHEMA_VERSION = 1;
	private static final String SUFFIX = ".json";
	private static final Pattern ACCOUNT_ID = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();
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

	AccountStore(@NotNull Path accountsDirectory) {
		directory = accountsDirectory.toAbsolutePath().normalize();
	}

	/**
	 * Lists the stored accounts; unreadable files are left out.
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
			try {
				accounts.add(read(fileName.substring(0, fileName.length() - SUFFIX.length())).metadata());
			} catch (RuntimeException ignored) {
				// Invalid files remain visible to repair tooling through their file name.
			}
		}

		return List.copyOf(accounts);
	}

	/**
	 * Reads a stored account, reading a file of the previous schema into the current shape.
	 *
	 * @param accountId owner-local account ID
	 * @return the stored account
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 * @throws IllegalStateException when no such account is stored, or its file is invalid or uses an unknown schema
	 * @throws IOException when the file cannot be read
	 */
	@NotNull StoredAccount read(@NotNull String accountId) throws IOException {
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
		if (version.asInt() == ACCOUNT_PER_LIBRARY_SCHEMA_VERSION) return readAccountPerLibrary(accountId, object);
		if (version.asInt() != SCHEMA_VERSION)
			throw new IllegalStateException("Stored account '" + accountId + "' uses schema version " + version.asInt()
					+ "; this Anvil version reads versions " + ACCOUNT_PER_LIBRARY_SCHEMA_VERSION + " and " + SCHEMA_VERSION);

		return readCurrent(accountId, object);
	}

	/**
	 * Stores an account, replacing its file atomically with owner-only permissions.
	 *
	 * @param account account to store
	 * @throws IllegalArgumentException when the account ID is not a valid file name
	 * @throws IOException when the file cannot be written
	 */
	void write(@NotNull StoredAccount account) throws IOException {
		String accountId = account.getAccountId();
		Path target = accountFile(accountId);

		ObjectNode document = JSON.createObjectNode();
		document.put("schemaVersion", SCHEMA_VERSION);
		document.put("accountId", accountId);
		document.set("microsoft", token(account.getToken()));
		ObjectNode libraries = document.putObject("libraries");
		account.getLibraries().forEach((libraryId, entry) -> libraries.set(libraryId, library(entry)));

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
	 * Removes a stored account.
	 *
	 * @param accountId owner-local account ID
	 * @return whether a file was removed
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 * @throws IOException when the file cannot be removed
	 */
	boolean delete(@NotNull String accountId) throws IOException {
		return Files.deleteIfExists(accountFile(accountId));
	}

	/**
	 * Returns the lock that serializes reading, refreshing and writing one account within this JVM, so two libraries
	 * deriving sessions from the same account never store an older Microsoft token over a newer one.
	 *
	 * @param accountId owner-local account ID
	 * @return monitor shared by every store of the same directory
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 */
	@NotNull Object lock(@NotNull String accountId) {
		return LOCKS.computeIfAbsent(accountFile(accountId), ignored -> new Object());
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

	private StoredAccount readCurrent(String accountId, ObjectNode document) {
		JsonNode microsoft = document.path("microsoft");
		if (!microsoft.isObject()) throw new IllegalStateException("Stored account '" + accountId + "' has no Microsoft token");

		StoredAccount.StoredAccountBuilder account = StoredAccount.builder()
				.accountId(accountId)
				.token(token(accountId, microsoft));
		Iterator<Map.Entry<String, JsonNode>> libraries = document.path("libraries").fields();
		while (libraries.hasNext()) {
			Map.Entry<String, JsonNode> library = libraries.next();
			account.library(library.getKey(), library(accountId, library.getKey(), library.getValue()));
		}

		return account.build();
	}

	/**
	 * Reads a file of the schema in which an account belonged to the one library its {@code provider} key names, and
	 * whose {@code credentials} are that library's saved MinecraftAuth session: its {@code msaApplicationConfig} and
	 * {@code msaToken} become the account's Microsoft token.
	 */
	private StoredAccount readAccountPerLibrary(String accountId, ObjectNode document) {
		JsonNode provider = document.path("provider");
		JsonNode credentials = document.path("credentials");
		if (!provider.isTextual() || provider.asText().isBlank() || !credentials.isObject())
			throw new IllegalStateException("Stored account '" + accountId + "' has no library or credentials");

		JsonNode application = credentials.path("msaApplicationConfig");
		JsonNode token = credentials.path("msaToken");
		if (!application.path("clientId").isTextual() || !application.path("scope").isTextual()
				|| !token.path("accessToken").isTextual() || !token.path("expireTimeMs").canConvertToLong())
			throw new IllegalStateException("Stored account '" + accountId + "' holds no Microsoft token; sign it in again");
		if (!application.path("clientSecret").isNull() && !application.path("clientSecret").isMissingNode()
				|| !application.path("environment").isMissingNode() && !application.path("environment").asText().equals("LIVE"))
			throw new IllegalStateException("Stored account '" + accountId + "' was signed in through a Microsoft application "
					+ "this Anvil version cannot refresh; sign it in again");

		AccountAccess access = AccountAccess.unknown(provider.asText(), null);
		try {
			if (document.path("username").isTextual() && document.path("uuid").isTextual())
				access = AccountAccess.available(provider.asText(), document.get("username").asText(),
						UUID.fromString(document.get("uuid").asText()));
		} catch (IllegalArgumentException invalid) {
			throw new IllegalStateException("Stored account '" + accountId + "' has an invalid profile UUID");
		}

		return StoredAccount.builder()
				.accountId(accountId)
				.token(MicrosoftToken.builder()
						.clientId(application.get("clientId").asText())
						.scope(application.get("scope").asText())
						.accessToken(token.get("accessToken").asText())
						.refreshToken(token.path("refreshToken").isTextual() ? token.get("refreshToken").asText() : null)
						.expiresAt(Instant.ofEpochMilli(token.get("expireTimeMs").asLong()))
						.build())
				.library(provider.asText(), new LibraryEntry(access, credentials.toString()))
				.build();
	}

	private static MicrosoftToken token(String accountId, JsonNode microsoft) {
		if (!microsoft.path("clientId").isTextual() || !microsoft.path("scope").isTextual()
				|| !microsoft.path("accessToken").isTextual() || !microsoft.path("expiresAt").canConvertToLong())
			throw new IllegalStateException("Stored account '" + accountId + "' has an invalid Microsoft token");

		return MicrosoftToken.builder()
				.clientId(microsoft.get("clientId").asText())
				.scope(microsoft.get("scope").asText())
				.accessToken(microsoft.get("accessToken").asText())
				.refreshToken(microsoft.path("refreshToken").isTextual() ? microsoft.get("refreshToken").asText() : null)
				.expiresAt(Instant.ofEpochMilli(microsoft.get("expiresAt").asLong()))
				.build();
	}

	private static ObjectNode token(MicrosoftToken token) {
		ObjectNode microsoft = JSON.createObjectNode();
		microsoft.put("clientId", token.getClientId());
		microsoft.put("scope", token.getScope());
		microsoft.put("accessToken", token.getAccessToken());
		microsoft.put("refreshToken", token.getRefreshToken());
		microsoft.put("expiresAt", token.getExpiresAt().toEpochMilli());

		return microsoft;
	}

	private static LibraryEntry library(String accountId, String libraryId, JsonNode library) {
		String availability = library.path("availability").asText();
		String reason = library.path("reason").isTextual() ? library.get("reason").asText() : null;
		AccountAccess access;
		try {
			access = switch (AccountAvailability.valueOf(availability)) {
				case AVAILABLE -> AccountAccess.available(libraryId, library.path("username").asText(),
						UUID.fromString(library.path("uuid").asText()));
				case UNAVAILABLE -> AccountAccess.unavailable(libraryId, reason == null ? "" : reason);
				case UNKNOWN -> AccountAccess.unknown(libraryId, reason);
			};
		} catch (IllegalArgumentException invalid) {
			throw new IllegalStateException("Stored account '" + accountId + "' records library '" + libraryId + "' invalidly");
		}

		return new LibraryEntry(access, library.path("session").isTextual() ? library.get("session").asText() : null);
	}

	private static ObjectNode library(LibraryEntry entry) {
		AccountAccess access = entry.access();
		ObjectNode library = JSON.createObjectNode();
		library.put("availability", access.getAvailability().name());
		if (access.getUsername() != null) library.put("username", access.getUsername());
		if (access.getUniqueId() != null) library.put("uuid", access.getUniqueId().toString());
		if (access.getReason() != null) library.put("reason", access.getReason());
		if (entry.cachedSession() != null) library.put("session", entry.cachedSession());

		return library;
	}

	private static void applyPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		if (Files.getFileStore(path).supportsFileAttributeView("posix"))
			Files.setPosixFilePermissions(path, permissions);
	}

	/**
	 * One stored account: its Microsoft token and, for each library that used it, what the library recorded.
	 */
	@Value
	@Builder(toBuilder = true)
	static class StoredAccount {
		@NotNull String accountId;
		@NotNull MicrosoftToken token;
		@Singular
		@NotNull Map<String, LibraryEntry> libraries;

		@NotNull AuthenticationAccount metadata() {
			return new AuthenticationAccount(accountId, libraries.values().stream().map(LibraryEntry::access).toList());
		}

		@Nullable String cachedSession(@NotNull String libraryId) {
			LibraryEntry entry = libraries.get(libraryId);
			return entry == null ? null : entry.cachedSession();
		}
	}

	/**
	 * What one library recorded about an account, and the session it cached for the next derivation.
	 */
	record LibraryEntry(@NotNull AccountAccess access, @Nullable String cachedSession) { }
}
