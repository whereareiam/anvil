package me.whereareiam.anvil.integration.intellij.account.persistence;

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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.integration.intellij.model.account.AvailableAccount;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;
import org.jetbrains.annotations.NotNull;

/**
 * Persists and reads account JSON files in one account directory.
 *
 * <p>This repository does not merge project and global sources or decide duplicate policy. Those
 * decisions belong to {@link ConfiguredAccountLibrary}.
 */
final class AccountDirectoryRepository {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern ACCOUNT_ID = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final Set<PosixFilePermission> PRIVATE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE
	);

	@NotNull Listing readAccounts(@NotNull Path directory, @NotNull AccountSource source) {
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
					AuthenticationAccount account = readMetadata(file);
					if (!file.getFileName().toString().equals(account.getAccountId() + ".json"))
						throw new IOException("File name must match the account ID.");

					accounts.add(new AvailableAccount(account, file, source));
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

	@NotNull AuthenticationAccount importAccount(
			@NotNull Path source,
			@NotNull Path directory
	) throws IOException {
		AuthenticationAccount account = readMetadata(source);
		Files.createDirectories(directory);
		copyOpaqueFile(source, directory.resolve(account.getAccountId() + ".json"));

		return account;
	}

	void exportAccount(
			@NotNull AvailableAccount account,
			@NotNull Path destination
	) throws IOException {
		copyOpaqueFile(account.getFile(), destination);
	}

	void removeAccount(@NotNull AvailableAccount account) throws IOException {
		if (account.getSource() != AccountSource.PROJECT) {
			throw new IOException("Global accounts cannot be removed from this project.");
		}

		Files.delete(account.getFile());
	}

	void copyAccountFiles(@NotNull Path source, @NotNull Path target) throws IOException {
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
	 * the libraries the file records and what each recorded. A file of schema 1, stored while an account belonged to
	 * the one library its {@code provider} key names, records that library.
	 */
	@NotNull AuthenticationAccount readMetadata(@NotNull Path source) throws IOException {
		JsonNode root;
		try (var input = Files.newInputStream(source)) {
			root = JSON.readTree(input);
		} catch (IOException failure) {
			// Parser exceptions may include credential values from the document.
			throw new IOException("Cannot read this account file.");
		}

		if (root == null || !isSafeAccountId(root.path("accountId").asText()))
			throw new IOException("This is not a supported Anvil account file.");

		String accountId = root.path("accountId").asText();
		int schema = root.path("schemaVersion").asInt();
		if (schema == 1 && !root.path("provider").asText().isBlank() && root.path("credentials").isObject())
			return new AuthenticationAccount(accountId, List.of(accountPerLibrary(root)));
		if (schema == 2 && root.path("microsoft").isObject())
			return new AuthenticationAccount(accountId, libraries(root.path("libraries")));

		throw new IOException("This is not a supported Anvil account file.");
	}

	private static AccountAccess accountPerLibrary(JsonNode root) throws IOException {
		String library = root.path("provider").asText();
		if (!root.path("username").isTextual() || !root.path("uuid").isTextual()) return AccountAccess.unknown(library, null);

		return AccountAccess.available(library, root.path("username").asText(), uniqueId(root.path("uuid").asText()));
	}

	private static List<AccountAccess> libraries(JsonNode libraries) throws IOException {
		List<AccountAccess> access = new ArrayList<>();
		var entries = libraries.fields();
		while (entries.hasNext()) {
			var entry = entries.next();
			String library = entry.getKey();
			JsonNode recorded = entry.getValue();
			String reason = recorded.path("reason").isTextual() ? recorded.path("reason").asText() : null;
			switch (recorded.path("availability").asText()) {
				case "AVAILABLE" -> access.add(AccountAccess.available(library, recorded.path("username").asText(),
						uniqueId(recorded.path("uuid").asText())));
				case "UNAVAILABLE" -> access.add(AccountAccess.unavailable(library, reason == null ? "" : reason));
				case "UNKNOWN" -> access.add(AccountAccess.unknown(library, reason));
				default -> throw new IOException("The account records library '" + library + "' invalidly.");
			}
		}

		return access;
	}

	private static UUID uniqueId(String text) throws IOException {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException failure) {
			throw new IOException("The account contains an invalid profile UUID.");
		}
	}

	boolean isSafeAccountId(@NotNull String id) {
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

	record Listing(@NotNull List<AvailableAccount> accounts, @NotNull List<String> problems) {}
}
