package me.whereareiam.anvil.integration.intellij.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads version 1 launch manifests into runner and authentication JVM commands.
 */
public final class ManifestToolingCommands implements ToolingCommands {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final int SCHEMA_VERSION = 1;
	private static final String RUNNER = "me.whereareiam.anvil.tooling.launcher.AnvilTooling";
	private static final String AUTHENTICATION = "me.whereareiam.anvil.tooling.launcher.AnvilAuthentication";

	@Override
	public @NotNull List<String> runner(@NotNull Path manifest, @Nullable Path accountsDirectory) throws IOException {
		JsonNode root = read(manifest);
		List<String> command = javaCommand(root, RUNNER, accountsDirectory, true);
		root.path("definitions").forEach(definition -> command.add(definition.asText()));

		return List.copyOf(command);
	}

	/**
	 * Builds the sign-in command. The account is one Microsoft account, so the command names no protocol library;
	 * every library derives its session from the account when it first uses it.
	 */
	@Override
	public @NotNull List<String> authentication(
			@NotNull Path manifest,
			@NotNull Path accountsDirectory,
			@NotNull String accountId
	) throws IOException {
		JsonNode root = read(manifest);
		List<String> command = javaCommand(root, AUTHENTICATION, null, false);
		command.add("--accounts-dir");
		command.add(accountsDirectory.toAbsolutePath().toString());
		command.add("--account-id");
		command.add(accountId);

		return List.copyOf(command);
	}

	private static @NotNull JsonNode read(@NotNull Path manifest) throws IOException {
		try (var input = Files.newInputStream(manifest)) {
			JsonNode root = JSON.readTree(input);
			if (root == null || root.path("schemaVersion").asInt() != SCHEMA_VERSION)
				throw new IOException("Unsupported Anvil tooling manifest. Update the project and IDE plugin together.");

			return root;
		}
	}

	private static @NotNull List<String> javaCommand(
			@NotNull JsonNode root,
			@NotNull String entrypoint,
			@Nullable Path accountsDirectory,
			boolean includeProperties
	) throws IOException {
		String executable = root.path("toolingJavaExecutable").asText();
		if (executable.isBlank()) throw new IOException("Missing toolingJavaExecutable in the Anvil tooling manifest.");

		JsonNode classpath = root.path("classpath");
		if (!classpath.isArray() || classpath.isEmpty())
			throw new IOException("The Anvil tooling manifest contains no runtime classpath.");

		List<String> command = new ArrayList<>();
		command.add(executable);
		if (includeProperties)
			root.path("properties").properties()
					.forEach(entry -> command.add("-D" + entry.getKey() + "=" + entry.getValue().asText()));
		if (accountsDirectory != null) command.add("-Danvil.accountsDir=" + accountsDirectory.toAbsolutePath());

		List<String> entries = new ArrayList<>();
		classpath.forEach(entry -> entries.add(entry.asText()));
		command.add("-cp");
		command.add(String.join(File.pathSeparator, entries));
		command.add(entrypoint);

		return command;
	}
}
