package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * A process the workbench prepared, with the addresses execution assigned it.
 */
@Value
public class PreparedTarget {
	/**
	 * Handle of the target.
	 */
	@NotNull String handle;

	/**
	 * Where the engine reaches the process's game port.
	 */
	@NotNull InetSocketAddress address;

	/**
	 * Where its peers reach it.
	 */
	@NotNull InetSocketAddress peerAddress;

	/**
	 * Address it binds.
	 */
	@NotNull String bindAddress;

	/**
	 * Where the engine reaches its agent, or null when it runs none.
	 */
	@Nullable InetSocketAddress agentAddress;

	/**
	 * Port its agent binds.
	 */
	int agentPort;

	/**
	 * Address its agent binds, or null when it runs none.
	 */
	@Nullable String agentBindAddress;

	/**
	 * Why it runs no agent, or null when it does.
	 */
	@Nullable String noAgent;

	/**
	 * Where the engine reaches its listeners.
	 */
	@NotNull Map<String, InetSocketAddress> listeners;

	/**
	 * Where its peers reach its listeners.
	 */
	@NotNull Map<String, InetSocketAddress> peerListeners;
}
