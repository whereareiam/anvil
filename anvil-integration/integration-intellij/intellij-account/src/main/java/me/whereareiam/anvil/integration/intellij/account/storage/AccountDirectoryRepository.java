package me.whereareiam.anvil.integration.intellij.account.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.AccountSource;
import me.whereareiam.anvil.integration.intellij.account.AvailableAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persists and reads account JSON files in one account directory.
 *
 * <p>This repository does not merge project and global sources or decide duplicate policy. Those
 * decisions belong to {@link AccountLibrary}.
 */
public final class AccountDirectoryRepository {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern ACCOUNT_ID = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final Set<PosixFilePermission> PRIVATE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE
	);

	public @NotNull Listing readAccounts(@NotNull Path directory, @NotNull AccountSource source) {
		List<AvailableAccount> accounts = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		readDirectory(directory, source, accounts, problems);
		accounts.sort(Comparator.comparing(account -> account.getAccount().getAccountId()));

		return new Listing(List.copyOf(accounts), List.copyOf(problems));
	}

	private void readDirectory(
			@NotNull Path directory,
			@NotNull AccountSource source,
			@NotNull List<AvailableAccount> accounts,
			@NotNull List<String> problems
	) {
		if (!Files.exists(directory)) return;
		try (var files = Files.list(directory)) {
			for (Path file : files
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList())
			{
				try {
					JsonNode document = readDocument(file);
					AuthenticationAccount account = metadata(document);
					if (!file.getFileName().toString().equals(account.getAccountId() + ".json"))
						throw new IOException("File name must match the account ID.");

					accounts.add(new AvailableAccount(account, libraryProfiles(document), file, source));
				} catch (IOException failure) {
					problems.add(
							source.getLabel()
									+ " account '"
									+ file.getFileName()
									+ "': "
									+ failure.getMessage()
					);
				}
			}
		} catch (IOException failure) {
			problems.add(
					"Cannot read "
							+ source.getLabel().toLowerCase()
							+ " account directory: "
							+ directory
			);
		}
	}

	public @NotNull AuthenticationAccount importAccount(
			@NotNull Path source,
			@NotNull Path directory
	) throws IOException {
		AuthenticationAccount account = readMetadata(source);
		Files.createDirectories(directory);
		copyOpaqueFile(source, directory.resolve(account.getAccountId() + ".json"));

		return account;
	}

	public void exportAccount(
			@NotNull AvailableAccount account,
			@NotNull Path destination
	) throws IOException {
		copyOpaqueFile(account.getFile(), destination);
	}

	public void removeAccount(@NotNull AvailableAccount account) throws IOException {
		if (account.getSource() != AccountSource.PROJECT) {
			throw new IOException("Global accounts cannot be removed from this project.");
		}

		Files.delete(account.getFile());
	}

	public void copyAccountFiles(@NotNull Path source, @NotNull Path target) throws IOException {
		if (!Files.isDirectory(source)) return;
		Files.createDirectories(target);

		try (var files = Files.list(source)) {
			for (Path file : files
					.filter(AccountDirectoryRepository::isAccountFile)
					.toList()) {
				Path destination = target.resolve(file.getFileName());
				if (Files.exists(destination)) {
					throw new IOException("Account file exists in both stores: " + file.getFileName());
				}

				copyOpaqueFile(file, destination);
			}
		}
	}

	/**
	 * Reads what an account file tells about the account without its credentials and without any protocol library:
	 * what was recorded for each Minecraft edition. Files of schema 1 and 2 recorded availability per protocol library,
	 * which only the runtime maps to editions, so they show every edition as not yet checked until the runtime next
	 * writes the file; their profiles are read by {@link #libraryProfiles(JsonNode)}.
	 */
	public @NotNull AuthenticationAccount readMetadata(@NotNull Path source) throws IOException {
		return metadata(readDocument(source));
	}

	private static JsonNode readDocument(Path source) throws IOException {
		try (var input = Files.newInputStream(source)) {
			return JSON.readTree(input);
		} catch (IOException failure) {
			// Parser exceptions may include credential values from the document.
			throw new IOException("Cannot read this account file.");
		}
	}

	private AuthenticationAccount metadata(JsonNode root) throws IOException {
		if (root == null || !isSafeAccountId(root.path("accountId").asText()))
			throw new IOException("This is not a supported Anvil account file.");

		String accountId = root.path("accountId").asText();
		int schema = root.path("schemaVersion").asInt();
		if (schema == 1 && !root.path("provider").asText().isBlank() && root.path("credentials").isObject())
			return new AuthenticationAccount(accountId, List.of());
		if (schema == 2 && root.path("microsoft").isObject())
			return new AuthenticationAccount(accountId, List.of());
		if (schema == 3 && root.path("microsoft").isObject())
			return new AuthenticationAccount(accountId, editions(root.path("editions")));

		throw new IOException("This is not a supported Anvil account file.");
	}

	/**
	 * Reads the profiles a file of schema 1 or 2 recorded for its protocol libraries: the {@code username} and
	 * {@code uuid} beside the {@code provider} of schema 1, and those of each available library of schema 2, without
	 * repeating one profile. A file of the current schema records its profiles per edition and has none here.
	 */
	private static List<PlayerProfile> libraryProfiles(JsonNode root) throws IOException {
		int schema = root.path("schemaVersion").asInt();
		if (schema == 1) {
			PlayerProfile profile = profile(root);
			return profile == null ? List.of() : List.of(profile);
		}
		if (schema != 2) return List.of();

		Set<PlayerProfile> profiles = new LinkedHashSet<>();
		for (JsonNode recorded : root.path("libraries")) {
			if (!recorded.path("availability").asText().equals("AVAILABLE")) continue;

			PlayerProfile profile = profile(recorded);
			if (profile != null) profiles.add(profile);
		}

		return List.copyOf(profiles);
	}

	private static @Nullable PlayerProfile profile(JsonNode recorded) throws IOException {
		if (!recorded.path("username").isTextual() || !recorded.path("uuid").isTextual()) return null;

		return new PlayerProfile(recorded.get("username").asText(), uniqueId(recorded.get("uuid").asText()));
	}

	private static List<AccountAccess> editions(JsonNode editions) throws IOException {
		List<AccountAccess> access = new ArrayList<>();
		var entries = editions.fields();
		while (entries.hasNext()) {
			var entry = entries.next();
			Edition edition = edition(entry.getKey());
			JsonNode recorded = entry.getValue();
			String reason = recorded.path("reason").isTextual() ? recorded.path("reason").asText() : null;
			switch (recorded.path("availability").asText()) {
				case "AVAILABLE" -> access.add(AccountAccess.available(edition, recorded.path("username").asText(),
						uniqueId(recorded.path("uuid").asText())));
				case "UNAVAILABLE" -> access.add(AccountAccess.unavailable(edition, reason == null ? "" : reason));
				case "UNKNOWN" -> access.add(AccountAccess.unknown(edition, reason));
				default -> throw new IOException("The account records edition '" + edition + "' invalidly.");
			}
		}

		return access;
	}

	private static Edition edition(String id) throws IOException {
		try {
			return Edition.of(id);
		} catch (IllegalArgumentException failure) {
			throw new IOException("The account records an invalid edition.");
		}
	}

	private static UUID uniqueId(String text) throws IOException {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException failure) {
			throw new IOException("The account contains an invalid profile UUID.");
		}
	}

	public boolean isSafeAccountId(@NotNull String id) {
		return !id.equals(".") && !id.equals("..") && ACCOUNT_ID.matcher(id).matches();
	}

	private static boolean isAccountFile(@NotNull Path path) {
		return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json");
	}

	static void copyOpaqueFile(@NotNull Path source, @NotNull Path target) throws IOException {
		Path parent = target.toAbsolutePath().getParent();
		boolean posix = Files.getFileStore(parent).supportsFileAttributeView("posix");
		Path temporary =
				posix
						? Files.createTempFile(
								parent,
								".anvil-account-",
								".tmp",
								PosixFilePermissions.asFileAttribute(PRIVATE_PERMISSIONS))
						: Files.createTempFile(parent, ".anvil-account-", ".tmp");

		try {
			try (var input = Files.newInputStream(source); var output = Files.newOutputStream(temporary)) {
				input.transferTo(output);
			}

			try {
				Files.move(
						temporary,
						target,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public record Listing(@NotNull List<AvailableAccount> accounts, @NotNull List<String> problems) {}
}
