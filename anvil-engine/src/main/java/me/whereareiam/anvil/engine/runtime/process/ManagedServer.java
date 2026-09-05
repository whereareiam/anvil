package me.whereareiam.anvil.engine.runtime.process;

import me.whereareiam.anvil.api.runtime.RunningServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Process-backed runtime view of a Minecraft server.
 */
public final class ManagedServer extends ManagedProcess implements RunningServer {
	/**
	 * Creates a not-yet-started managed server.
	 */
	public ManagedServer(String name, InetSocketAddress address, Path workDirectory) {
		super(name, address, workDirectory);
	}
}
