package me.whereareiam.anvil.engine.runtime.process;

import me.whereareiam.anvil.api.runtime.RunningProxy;

import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Process-backed runtime view of a Minecraft proxy.
 */
public final class ManagedProxy extends ManagedProcess implements RunningProxy {
	/**
	 * Creates a not-yet-started managed proxy.
	 */
	public ManagedProxy(String name, InetSocketAddress address, Path workDirectory) {
		super(name, address, workDirectory);
	}
}
