package me.whereareiam.anvil.protocol.cloudburst.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * Where a Bedrock client believes its own player is, as Bedrock packets carry a player's position: at the eyes,
 * 1.62 blocks above the feet of a standing player.
 *
 * <pre>{@code
 * ClientPosition position = session.position();
 * use.setPlayerPosition(Vector3f.from(position.getX(), position.getY(), position.getZ()));
 * }</pre>
 */
@Value
@Builder
public class ClientPosition {
	/**
	 * Position on the x axis.
	 */
	float x;
	/**
	 * Position of the eyes on the y axis.
	 */
	float y;
	/**
	 * Position on the z axis.
	 */
	float z;
}
