package me.whereareiam.anvil.launcher.adapter.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.ProcessAddresses;
import me.whereareiam.anvil.environment.execution.api.model.ImageLaunch;
import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedLaunch;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedProcess;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;

/**
 * The prepared inputs of a container process: its environment and command with the addresses of the processes it
 * names filled in. It has no workspace files, agent or capabilities; its directory only keeps its console log.
 */
@RequiredArgsConstructor
final class PreparedContainer implements PreparedProcess {
	private final ImageLaunch launch;

	/**
	 * Prepares a container process.
	 *
	 * @param container the container's declaration
	 * @param process   its execution declaration
	 * @param addresses where the container reaches the other processes
	 * @return the prepared container
	 */
	static @NotNull PreparedContainer prepare(
			@NotNull ContainerProcess container,
			@NotNull ProcessSpec process,
			@NotNull ProcessAddresses addresses
	) {
		try {
			Files.createDirectories(process.getRequest().getWorkspace());
		} catch (IOException failure) {
			throw new ProvisioningException("Could not prepare the directory of container '" + container.getName() + "'", failure);
		}

		ImageLaunch.ImageLaunchBuilder launch = ImageLaunch.builder();
		container.getEnvironment().forEach((name, value) -> launch.environmentVariable(name, addresses.resolve(value)));
		container.getCommand().forEach(argument -> launch.argument(addresses.resolve(argument)));

		return new PreparedContainer(launch.build());
	}

	@Override
	public @NotNull PreparedLaunch launch() {
		return new PreparedLaunch() {
			@Override
			public @NotNull ProcessLaunch command() {
				return launch;
			}

			@Override
			public void started() {
			}

			@Override
			public void close() {
			}
		};
	}

	@Override
	public void finish(boolean successful) {
		// The container's directory goes with the scenario's run directory.
	}
}
