package me.whereareiam.anvil.environment.execution.docker.execution;

import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.docker.DockerEngine;
import org.jetbrains.annotations.NotNull;

/**
 * Executes a scenario in containers on the local Docker daemon.
 */
public final class DockerExecutionProvider implements ExecutionProvider {
	private final DockerExecutionSettings settings;

	/**
	 * Creates the service-discovered provider with no image overrides.
	 *
	 * <p>Image mappings can be supplied through the explicit constructor when Docker execution is
	 * configured by an embedding application.</p>
	 */
	public DockerExecutionProvider() {
		this(DockerExecutionSettings.builder().build());
	}

	/**
	 * Creates a Docker execution provider with the supplied image mappings.
	 *
	 * @param settings Docker image configuration
	 */
	public DockerExecutionProvider(@NotNull DockerExecutionSettings settings) {
		this.settings = settings;
	}

	@Override
	public @NotNull String id() {
		return "docker";
	}

	@Override
	public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
		DockerEngine docker = new DockerEngine();
		try {
			return new DockerExecutionSession(context, docker, settings);
		} catch (RuntimeException failure) {
			try {
				docker.close();
			} catch (RuntimeException cleanup) {
				failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}
}
