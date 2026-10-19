package me.whereareiam.anvil.environment.service.docker;

import me.whereareiam.anvil.environment.service.api.ServiceProvider;
import me.whereareiam.anvil.environment.service.api.ServiceSession;
import me.whereareiam.anvil.environment.service.api.model.ServiceContext;
import org.jetbrains.annotations.NotNull;

/**
 * Runs services as containers of the machine's Docker engine.
 */
public final class DockerServiceProvider implements ServiceProvider {
	@Override
	public @NotNull String id() {
		return "docker";
	}

	@Override
	public @NotNull ServiceSession open(@NotNull ServiceContext context) {
		return new DockerServiceSession(context);
	}
}
