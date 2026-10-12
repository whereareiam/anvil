package me.whereareiam.anvil.protocol.cloudburst.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * What a Bedrock server's game start tells the client about its own player: the entity ids packets address it by,
 * where it spawned and how it looks, and the server tick at that moment.
 *
 * <pre>{@code
 * GameStart start = session.start().orElseThrow();
 * input.setPosition(Vector3f.from(start.getX(), start.getY(), start.getZ()));
 * }</pre>
 */
@Value
@Builder
public class GameStart {
	/**
	 * Runtime entity id of the player, which most packets about the player carry.
	 */
	long runtimeEntityId;
	/**
	 * Unique entity id of the player, which the player list and a few packets carry.
	 */
	long uniqueEntityId;
	/**
	 * Spawn position on the x axis.
	 */
	float x;
	/**
	 * Spawn position on the y axis, as the server sent it.
	 */
	float y;
	/**
	 * Spawn position on the z axis.
	 */
	float z;
	/**
	 * Spawn view yaw in degrees.
	 */
	float yaw;
	/**
	 * Spawn view pitch in degrees.
	 */
	float pitch;
	/**
	 * Server tick when the game started, from which the client counts its own ticks.
	 */
	long tick;
}
