package me.whereareiam.anvil.execution.local.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.execution.api.model.JavaCommand;
import me.whereareiam.anvil.execution.api.model.ProcessRequest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Retains a host workspace, executable, and endpoints across process replacements.
 */
@RequiredArgsConstructor
public final class LocalProcessTarget implements ProcessTarget {
	private final ProcessRequest request;
	private final Path executable;
	private final InetSocketAddress address;
	private final InetSocketAddress agentAddress;

	@Override
	public @NotNull ProcessExecution start(@NotNull JavaCommand command) {
		List<String> arguments = new ArrayList<>();
		arguments.add(executable.toString());
		arguments.add("-Xms256m");
		arguments.add("-Xmx" + command.getMemoryMegabytes() + "m");
		arguments.addAll(command.getJvmArguments());
		arguments.add("-jar");
		arguments.add(command.getJar().toAbsolutePath().toString());
		arguments.addAll(command.getArguments());

		try {
			Files.createDirectories(request.getWorkspace());
			ProcessBuilder builder = new ProcessBuilder(arguments)
					.directory(request.getWorkspace().toFile())
					.redirectErrorStream(true);
			builder.environment().putAll(command.getEnvironment());

			return new LocalProcess(builder.start());
		} catch (IOException failure) {
			throw new ProcessException(request.getName(), "Could not launch local Java process", failure);
		}
	}

	@Override
	public @NotNull InetSocketAddress address() {
		return address;
	}

	@Override
	public @NotNull InetSocketAddress peerAddress() {
		return address;
	}

	@Override
	public @NotNull String bindAddress() {
		return address.getHostString();
	}

	@Override
	public @NotNull InetSocketAddress agentAddress() {
		return agentAddress;
	}

	@Override
	public int agentPort() {
		return agentAddress.getPort();
	}

	@Override
	public @NotNull String agentBindAddress() {
		return "127.0.0.1";
	}

	@Override
	public void close() {
		// Workspace retention belongs to the scenario owner.
	}
}
