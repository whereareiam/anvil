package me.whereareiam.anvil.environment.execution.managed.process.type;

import me.whereareiam.anvil.api.process.type.RunningContainer;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.managed.process.ManagedProcess;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Process-backed runtime view of a container process, whose ports are its listeners by number.
 */
public final class ManagedContainer extends ManagedProcess implements RunningContainer {
	/**
	 * Creates a not-yet-started managed container.
	 */
	public ManagedContainer(
			@NotNull String name,
			@NotNull InetSocketAddress address,
			@NotNull Map<String, InetSocketAddress> ports,
			@NotNull Path workDirectory
	) {
		super(name, address, ports, workDirectory, null);
	}

	@Override
	public @NotNull InetSocketAddress port(int containerPort) {
		try {
			return listener(Integer.toString(containerPort));
		} catch (IllegalArgumentException missing) {
			throw new IllegalArgumentException("Container '" + name() + "' declares no port " + containerPort, missing);
		}
	}

	@Override
	public @NotNull CommandResult execute(@NotNull Duration timeout, @NotNull String... command) {
		ProcessExecution execution = currentExecution();
		if (execution == null || !execution.isAlive())
			throw new IllegalStateException("Container '" + name() + "' is not running");

		return execution.execute(timeout, command);
	}
}
