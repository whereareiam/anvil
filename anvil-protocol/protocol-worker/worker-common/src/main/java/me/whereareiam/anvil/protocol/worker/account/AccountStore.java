package me.whereareiam.anvil.protocol.worker.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import me.whereareiam.anvil.api.model.Edition;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The account files of the native worker: one private JSON file per account ID, holding one Microsoft account's
 * token, what was recorded about the account for each Minecraft edition, and the session each library cached.
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 3,
 *   "accountId": "alice",
 *   "microsoft": { "clientId": "...", "scope": "...", "accessToken": "...", "refreshToken": "...", "expiresAt": 0 },
 *   "editions": {
 *     "example": { "availability": "AVAILABLE", "username": "Alice", "uuid": "..." }
 *   },
 *   "sessions": { "examplelib": "..." }
 * }
 * }</pre>
 *
 * <p>Files of earlier schema versions recorded availability per library: version 2 under {@code libraries}, each
 * entry with the library's cached {@code session}, and version 1 for the one library its {@code provider} key names,
 * whose {@code credentials} are that library's saved MinecraftAuth session and hold the Microsoft token. They are read
 * into this shape: each library's cached session stays its own, and its record becomes the record of the edition the
 * store's {@code libraryEditions} maps it to, preferring an available record when two libraries share an edition.
 * The record of a library that maps to no edition is left out, so the edition is checked again. Writing such an
 * account stores it in the current schema.</p>
 */
final class AccountStore {
	private static final int SCHEMA_VERSION = 3;
	private static final int ACCOUNT_PER_LIBRARY_SCHEMA_VERSION = 1;
	private static final int AVAILABILITY_PER_LIBRARY_SCHEMA_VERSION = 2;
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
	private final Function<String, Optional<Edition>> libraryEditions;

	/**
	 * Creates the store of an accounts directory.
	 *
	 * @param accountsDirectory directory of the account files
	 * @param libraryEditions maps a library ID to the one edition its releases speak, which the record a file of an
	 * earlier schema keeps for the library becomes the record of; empty for a library that is not installed or speaks
	 * several editions
	 */
	AccountStore(@NotNull Path accountsDirectory, @NotNull Function<String, Optional<Edition>> libraryEditions) {
		directory = accountsDirectory.toAbsolutePath().normalize();
		this.libraryEditions = libraryEditions;
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
	 * Reads a stored account, reading a file of an earlier schema into the current shape.
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
		if (version.asInt() == AVAILABILITY_PER_LIBRARY_SCHEMA_VERSION) return readAvailabilityPerLibrary(accountId, object);
		if (version.asInt() != SCHEMA_VERSION)
			throw new IllegalStateException("Stored account '" + accountId + "' uses schema version " + version.asInt()
					+ "; this Anvil version reads versions " + ACCOUNT_PER_LIBRARY_SCHEMA_VERSION + " to " + SCHEMA_VERSION);

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
		ObjectNode editions = document.putObject("editions");
		account.getEditions().forEach((edition, access) -> editions.set(edition.getId(), access(access)));
		ObjectNode sessions = document.putObject("sessions");
		account.getSessions().forEach(sessions::put);

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
		StoredAccount account = StoredAccount.of(accountId, microsoft(accountId, document));
		Iterator<Map.Entry<String, JsonNode>> editions = document.path("editions").fields();
		while (editions.hasNext()) {
			Map.Entry<String, JsonNode> edition = editions.next();
			account = account.withAccess(access(accountId, editionOf(accountId, edition.getKey()), edition.getValue()));
		}

		Iterator<Map.Entry<String, JsonNode>> sessions = document.path("sessions").fields();
		while (sessions.hasNext()) {
			Map.Entry<String, JsonNode> session = sessions.next();
			if (session.getValue().isTextual()) account = account.withSession(session.getKey(), session.getValue().asText());
		}

		return account;
	}

	/**
	 * Reads a file of the schema that recorded availability per library, with each library's cached session.
	 */
	private StoredAccount readAvailabilityPerLibrary(String accountId, ObjectNode document) {
		StoredAccount account = StoredAccount.of(accountId, microsoft(accountId, document));
		Iterator<Map.Entry<String, JsonNode>> libraries = document.path("libraries").fields();
		while (libraries.hasNext()) {
			Map.Entry<String, JsonNode> library = libraries.next();
			JsonNode recorded = library.getValue();
			if (recorded.path("session").isTextual()) account = account.withSession(library.getKey(), recorded.get("session").asText());

			Optional<Edition> edition = libraryEditions.apply(library.getKey());
			if (edition.isPresent()) account = account.withEarlierRecord(access(accountId, edition.get(), recorded));
		}

		return account;
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

		StoredAccount account = StoredAccount.of(accountId, MicrosoftToken.builder()
						.clientId(application.get("clientId").asText())
						.scope(application.get("scope").asText())
						.accessToken(token.get("accessToken").asText())
						.refreshToken(token.path("refreshToken").isTextual() ? token.get("refreshToken").asText() : null)
						.expiresAt(Instant.ofEpochMilli(token.get("expireTimeMs").asLong()))
						.build())
				.withSession(provider.asText(), credentials.toString());
		Optional<Edition> edition = libraryEditions.apply(provider.asText());
		if (edition.isEmpty() || !document.path("username").isTextual() || !document.path("uuid").isTextual()) return account;

		try {
			return account.withAccess(AccountAccess.available(edition.get(), document.get("username").asText(),
					UUID.fromString(document.get("uuid").asText())));
		} catch (IllegalArgumentException invalid) {
			throw new IllegalStateException("Stored account '" + accountId + "' has an invalid profile UUID");
		}
	}

	private static MicrosoftToken microsoft(String accountId, ObjectNode document) {
		JsonNode microsoft = document.path("microsoft");
		if (!microsoft.isObject()) throw new IllegalStateException("Stored account '" + accountId + "' has no Microsoft token");

		return token(accountId, microsoft);
	}

	private static Edition editionOf(String accountId, String id) {
		try {
			return Edition.of(id);
		} catch (IllegalArgumentException invalid) {
			throw new IllegalStateException("Stored account '" + accountId + "' records an invalid edition");
		}
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

	private static AccountAccess access(String accountId, Edition edition, JsonNode recorded) {
		String reason = recorded.path("reason").isTextual() ? recorded.get("reason").asText() : null;
		try {
			return switch (AccountAvailability.valueOf(recorded.path("availability").asText())) {
				case AVAILABLE -> AccountAccess.available(edition, recorded.path("username").asText(),
						UUID.fromString(recorded.path("uuid").asText()));
				case UNAVAILABLE -> AccountAccess.unavailable(edition, reason == null ? "" : reason);
				case UNKNOWN -> AccountAccess.unknown(edition, reason);
			};
		} catch (IllegalArgumentException invalid) {
			throw new IllegalStateException("Stored account '" + accountId + "' records edition '" + edition + "' invalidly");
		}
	}

	private static ObjectNode access(AccountAccess access) {
		ObjectNode recorded = JSON.createObjectNode();
		recorded.put("availability", access.getAvailability().name());
		if (access.getUsername() != null) recorded.put("username", access.getUsername());
		if (access.getUniqueId() != null) recorded.put("uuid", access.getUniqueId().toString());
		if (access.getReason() != null) recorded.put("reason", access.getReason());

		return recorded;
	}

	private static void applyPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		if (Files.getFileStore(path).supportsFileAttributeView("posix"))
			Files.setPosixFilePermissions(path, permissions);
	}

	/**
	 * One stored account: its Microsoft token, what was recorded for each edition, and each library's cached session.
	 */
	@Value
	@With
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	static class StoredAccount {
		@NotNull String accountId;
		@NotNull MicrosoftToken token;
		@NotNull Map<Edition, AccountAccess> editions;
		@NotNull Map<String, String> sessions;

		static @NotNull StoredAccount of(@NotNull String accountId, @NotNull MicrosoftToken token) {
			return new StoredAccount(accountId, token, Map.of(), Map.of());
		}

		@NotNull AuthenticationAccount metadata() {
			return new AuthenticationAccount(accountId, editions.values());
		}

		@NotNull AccountAccess access(@NotNull Edition edition) {
			AccountAccess access = editions.get(edition);
			return access == null ? AccountAccess.unknown(edition, null) : access;
		}

		@Nullable String cachedSession(@NotNull String libraryId) {
			return sessions.get(libraryId);
		}

		/**
		 * Records what was determined for an edition, replacing the edition's earlier record.
		 */
		@NotNull StoredAccount withAccess(@NotNull AccountAccess access) {
			Map<Edition, AccountAccess> changed = new LinkedHashMap<>(editions);
			changed.put(access.getEdition(), access);
			return withEditions(Collections.unmodifiableMap(changed));
		}

		/**
		 * Keeps a library's cached session, or drops it when the session is null.
		 */
		@NotNull StoredAccount withSession(@NotNull String libraryId, @Nullable String session) {
			Map<String, String> changed = new LinkedHashMap<>(sessions);
			if (session == null) changed.remove(libraryId);
			else changed.put(libraryId, session);
			return withSessions(Collections.unmodifiableMap(changed));
		}

		/**
		 * Merges a record a file of an earlier schema kept for a library of the edition, preferring an available
		 * record over an unavailable one and either over an unknown one.
		 */
		private @NotNull StoredAccount withEarlierRecord(@NotNull AccountAccess access) {
			AccountAccess recorded = editions.get(access.getEdition());
			if (recorded != null && rank(recorded) >= rank(access)) return this;

			return withAccess(access);
		}

		private static int rank(AccountAccess access) {
			return switch (access.getAvailability()) {
				case AVAILABLE -> 2;
				case UNAVAILABLE -> 1;
				case UNKNOWN -> 0;
			};
		}
	}
}
