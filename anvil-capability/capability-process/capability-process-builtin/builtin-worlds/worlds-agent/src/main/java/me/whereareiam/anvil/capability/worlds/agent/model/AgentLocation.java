package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * A place in one of a server's worlds with a view direction, as the agent channel carries it.
 */
@Value
@Builder
@Jacksonized
public class AgentLocation {
	/**
	 * The key of the world's level, with its namespace.
	 */
	@NotNull String world;
	/**
	 * The position along the east-west axis.
	 */
	double x;
	/**
	 * The height.
	 */
	double y;
	/**
	 * The position along the north-south axis.
	 */
	double z;
	/**
	 * The horizontal view angle in degrees.
	 */
	float yaw;
	/**
	 * The vertical view angle in degrees.
	 */
	float pitch;
}
