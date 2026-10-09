package me.whereareiam.anvil.environment.execution.managed.process.type;

import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.environment.execution.managed.process.ManagedProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Process-backed runtime view of a Minecraft server.
 */
public final class ManagedServer extends ManagedProcess implements RunningServer {
	/**
	 * Creates a not-yet-started managed server.
	 */
	public ManagedServer(
			@NotNull String name,
			@NotNull InetSocketAddress address,
			@NotNull Path workDirectory,
			@Nullable CapabilityOwner<ProcessCapability> capabilities
	) {
		super(name, address, workDirectory, capabilities);
	}
}
