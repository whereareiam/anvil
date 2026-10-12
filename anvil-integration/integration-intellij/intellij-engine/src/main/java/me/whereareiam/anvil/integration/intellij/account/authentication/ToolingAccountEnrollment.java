package me.whereareiam.anvil.integration.intellij.account.authentication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingCommandReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns one account-enrollment operation, including its child processes and temporary manifest.
 */
public final class ToolingAccountEnrollment implements AccountEnrollment {
	private final @NotNull Callable<ScenarioPreparation> planner;
	private final @NotNull Path accountsDirectory;
	private final @NotNull String accountId;
	private final @NotNull Consumer<String> output;
	private final @NotNull ProcessLauncher processLauncher;
	private final @NotNull CompletableFuture<Void> completion = new CompletableFuture<>();

	private boolean started;
	private boolean cancelled;
	private @Nullable Process process;

	/**
	 * Creates an enrollment operation that launches processes with the host JVM's process builder.
	 */
	public ToolingAccountEnrollment(
			@NotNull Callable<ScenarioPreparation> planner,
			@NotNull Path accountsDirectory,
			@NotNull String accountId,
			@NotNull Consumer<String> output
	) {
		this(planner, accountsDirectory, accountId, output, ProcessBuilder::start);
	}

	ToolingAccountEnrollment(
			@NotNull Callable<ScenarioPreparation> planner,
			@NotNull Path accountsDirectory,
			@NotNull String accountId,
			@NotNull Consumer<String> output,
			@NotNull ProcessLauncher processLauncher
	) {
		this.planner = planner;
		this.accountsDirectory = accountsDirectory;
		this.accountId = accountId;
		this.output = output;
		this.processLauncher = processLauncher;
	}

	/**
	 * Starts this operation once on the supplied executor. Repeated calls return the original
	 * completion future and do not launch another preparation or authentication process.
	 *
	 * @param executor executor used for blocking preparation and child-process I/O
	 * @return completion after authentication and cleanup
	 */
	@Override
	public @NotNull CompletableFuture<Void> start(@NotNull Executor executor) {
		synchronized (this) {
			if (started) return completion;
			started = true;
			if (cancelled) {
				completion.completeExceptionally(cancellationFailure());
				return completion;
			}
		}

		try {
			executor.execute(this::runOperation);
		} catch (RuntimeException failure) {
			completion.completeExceptionally(failure);
		}

		return completion;
	}

	private void runOperation() {
		Path manifest = null;
		try {
			checkCancellation();
			output.accept("Preparing account sign-in…");

			ScenarioPreparation preparation = planner.call();
			if (preparation == null) {
				throw new IOException("The preparation planner returned no plan.");
			}

			manifest = preparation.getManifestPath();
			checkCancellation();
			runPreparation(preparation);
			checkCancellation();

			output.accept("Follow the sign-in instructions below. " +
					"The account will be saved to this project's account directory.");
			runAuthentication(preparation, manifest);
		} catch (Exception failure) {
			if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
			complete(manifest, failure);
			return;
		}

		complete(manifest, null);
	}

	private void runPreparation(@NotNull ScenarioPreparation preparation) throws IOException, InterruptedException {
		ProcessBuilder command = new ProcessBuilder(preparation.getCommand())
				.directory(preparation.getWorkingDirectory().toFile());
		command.environment().putAll(preparation.getEnvironment());
		runCommand(command, "Could not prepare account sign-in");
	}

	private void runAuthentication(
			@NotNull ScenarioPreparation preparation,
			@NotNull Path manifest
	) throws IOException, InterruptedException {
		runCommand(
				new ProcessBuilder(
						ToolingCommandReader.authenticationCommand(
								manifest, accountsDirectory, accountId, null))
						.directory(preparation.getWorkingDirectory().toFile()),
				"Sign-in failed"
		);
	}

	private void runCommand(
			@NotNull ProcessBuilder builder,
			@NotNull String failureMessage
	) throws IOException, InterruptedException {
		Process child = launch(builder.redirectErrorStream(true));
		try {
			streamOutput(child);
			int exitCode = child.waitFor();
			checkCancellation();
			if (exitCode != 0) {
				throw new IOException(
						failureMessage + ". Process exited with code " + exitCode + ".");
			}
		} finally {
			clearProcess(child);
		}
	}

	private void streamOutput(@NotNull Process child) throws IOException {
		try (var lines = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = lines.readLine()) != null) {
				checkCancellation();
				output.accept(line);
			}
		}
	}

	private synchronized @NotNull Process launch(@NotNull ProcessBuilder builder) throws IOException {
		checkCancellation();
		Process child = processLauncher.start(builder);
		process = child;
		return child;
	}

	private synchronized void clearProcess(@NotNull Process child) {
		if (process == child) process = null;
	}

	private void checkCancellation() {
		synchronized (this) {
			if (cancelled) throw cancellationFailure();
		}
	}

	private void complete(@Nullable Path manifest, @Nullable Throwable failure) {
		try {
			terminateActiveProcess();
		} catch (RuntimeException cleanupFailure) {
			failure = merge(failure, cleanupFailure);
		}

		if (manifest != null) {
			try {
				Files.deleteIfExists(manifest);
			} catch (IOException | RuntimeException cleanupFailure) {
				failure = merge(failure, cleanupFailure);
			}
		}

		if (failure == null) completion.complete(null);
		else completion.completeExceptionally(failure);
	}

	private void terminateActiveProcess() {
		Process active;
		synchronized (this) {
			active = process;
			process = null;
		}
		terminate(active);
	}

	private static void terminate(@Nullable Process child) {
		if (child == null || !child.isAlive()) return;

		try {
			child.descendants().forEach(ProcessHandle::destroyForcibly);
		} catch (UnsupportedOperationException ignored) {
			// Some in-memory process fixtures do not expose native process handles.
		} finally {
			child.destroyForcibly();
		}
	}

	private static @NotNull Throwable merge(
			@Nullable Throwable failure,
			@NotNull Throwable cleanupFailure
	) {
		if (failure == null) return cleanupFailure;
		failure.addSuppressed(cleanupFailure);

		return failure;
	}

	private static @NotNull CancellationException cancellationFailure() {
		return new CancellationException("Sign-in cancelled.");
	}

	@Override
	public void dispose() {
		boolean cancelBeforeStart;
		synchronized (this) {
			cancelled = true;
			cancelBeforeStart = !started;
		}

		terminateActiveProcess();
		if (cancelBeforeStart) completion.completeExceptionally(cancellationFailure());
	}

	interface ProcessLauncher {
		@NotNull Process start(@NotNull ProcessBuilder builder) throws IOException;
	}
}
