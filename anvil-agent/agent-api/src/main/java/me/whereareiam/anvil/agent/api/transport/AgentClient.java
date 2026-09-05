package me.whereareiam.anvil.agent.api.transport;

import me.whereareiam.anvil.agent.api.platform.access.PlatformCommandAccess;
import me.whereareiam.anvil.agent.api.platform.access.PlatformPlayerIdentityAccess;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnection;

/**
 * Typed host-side contract for a running platform agent.
 */
public interface AgentClient extends AgentConnection, PlatformPlayerIdentityAccess, PlatformCommandAccess {
	/**
	 * Closes the platform-agent connection.
	 */
	@Override
	void close();
}
