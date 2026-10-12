package me.whereareiam.anvil.integration.intellij.account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.integration.intellij.ControlledToolingProcess;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.source.ManifestToolingCommands;
import org.jetbrains.annotations.NotNull;

/**
 * Sign-ins whose preparation succeeds at once and whose authentication process the test controls.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccountSignInFixture {
	/**
	 * Creates a sign-in of account "alice" that runs the given process as its authentication step.
	 */
	public static @NotNull AccountSignIn controlled(
			@NotNull Path directory,
			@NotNull Consumer<String> output,
			@NotNull ControlledToolingProcess authentication
	) throws IOException {
		Path manifest = Files.writeString(directory.resolve("manifest.json"),
				"{\"schemaVersion\":1,\"toolingJavaExecutable\":\"java\",\"classpath\":[\"fixture.jar\"],\"definitions\":[]}");
		var preparation = new ControlledToolingProcess(false);
		preparation.complete(0);
		var processes = new ArrayDeque<>(List.of(preparation, authentication));
		ScenarioPreparation plan = ScenarioPreparation.builder()
				.command(List.of("prepare"))
				.workingDirectory(directory)
				.manifestPath(manifest)
				.build();

		return new AccountSignIn(() -> plan, new ManifestToolingCommands(), directory.resolve("accounts"), "alice", output,
				builder -> processes.removeFirst());
	}
}
