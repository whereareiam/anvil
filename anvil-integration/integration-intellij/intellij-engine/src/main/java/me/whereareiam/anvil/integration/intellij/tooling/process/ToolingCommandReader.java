package me.whereareiam.anvil.integration.intellij.tooling.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads prepared launch manifests into runner and authentication JVM commands.
 */
@UtilityClass
public class ToolingCommandReader {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final int SCHEMA_VERSION = 1;

	public static @NotNull List<String> command(@NotNull Path manifest) throws IOException {
		return command(manifest, null);
	}

	public static @NotNull List<String> command(
			@NotNull Path manifest,
			@Nullable Path accountsDirectory
	) throws IOException {
		JsonNode root = read(manifest);
		List<String> command = javaCommand(
				root, "me.whereareiam.anvil.tooling.launcher.AnvilTooling",
				accountsDirectory,
				true
		);

		root.path("definitions").forEach(definition -> command.add(definition.asText()));

		return List.copyOf(command);
	}

	/**
	 * Builds the account sign-in command for the prepared tooling runtime.
	 *
	 * @param manifest prepared launch manifest
	 * @param accountsDirectory account directory used by the IDE
	 * @param accountId account to sign in
	 * @param libraryId protocol library to sign in with, or null for the manifest's
	 *                  {@code anvil.protocolLibrary} property, then the runtime's sole library offering authentication
	 * @return authentication JVM command
	 * @throws IOException when the manifest cannot be read or is unsupported
	 */
	public static @NotNull List<String> authenticationCommand(
			@NotNull Path manifest,
			@NotNull Path accountsDirectory,
			@NotNull String accountId,
			@Nullable String libraryId
	) throws IOException {
		JsonNode root = read(manifest);
		List<String> command = javaCommand(
				root,
				"me.whereareiam.anvil.tooling.launcher.AnvilAuthentication",
				null,
				false
		);

		command.add("--accounts-dir");
		command.add(accountsDirectory.toAbsolutePath().toString());
		command.add("--account-id");
		command.add(accountId);

		String selectedLibrary = libraryId;
		if ((selectedLibrary == null || selectedLibrary.isBlank())
				&& root.path("properties").has("anvil.protocolLibrary"))
			selectedLibrary = root.path("properties").path("anvil.protocolLibrary").asText();

		if (selectedLibrary != null && !selectedLibrary.isBlank()) {
			command.add("--library");
			command.add(selectedLibrary);
		}

		return List.copyOf(command);
	}

	private static @NotNull JsonNode read(@NotNull Path manifest) throws IOException {
		try (var input = Files.newInputStream(manifest)) {
			JsonNode root = JSON.readTree(input);
			if (root == null || root.path("schemaVersion").asInt() != SCHEMA_VERSION)
				throw new IOException("Unsupported Anvil tooling manifest. " +
						"Update the project and IDE plugin together.");

			return root;
		}
	}

	private static @NotNull List<String> javaCommand(
			@NotNull JsonNode root,
			@NotNull String entrypoint,
			@Nullable Path accountsDirectory,
			boolean includeProperties
	) throws IOException {
		String executable = required(root);
		JsonNode classpath = root.path("classpath");
		if (!classpath.isArray() || classpath.isEmpty()) {
			throw new IOException("The Anvil tooling manifest " +
					"contains no runtime classpath.");
		}

		List<String> command = new ArrayList<>();
		command.add(executable);
		if (includeProperties) {
			root.path("properties")
					.properties()
					.forEach(entry -> command.add("-D" + entry.getKey() + "=" + entry.getValue().asText()));
		}

		if (accountsDirectory != null) {
			command.add("-Danvil.accountsDir=" + accountsDirectory.toAbsolutePath());
		}

		command.add("-cp");

		List<String> entries = new ArrayList<>();
		classpath.forEach(entry -> entries.add(entry.asText()));
		command.add(String.join(File.pathSeparator, entries));
		command.add(entrypoint);

		return command;
	}

	private static @NotNull String required(@NotNull JsonNode root) throws IOException {
		String value = root.path("toolingJavaExecutable").asText();
		if (value.isBlank()) {
			throw new IOException("Missing toolingJavaExecutable " +
					"in the Anvil tooling manifest.");
		}

		return value;
	}
}
