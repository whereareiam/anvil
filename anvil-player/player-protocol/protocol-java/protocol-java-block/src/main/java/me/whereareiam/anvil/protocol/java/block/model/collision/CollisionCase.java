package me.whereareiam.anvil.protocol.java.block.model.collision;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.block.type.EntityBottom;
import org.jetbrains.annotations.NotNull;

/**
 * One player a block state's collision shape was read for, through {@code CollisionContext.of(player)} as vanilla's
 * entity collision reads it: the inputs of that context a player's movement changes. A player that holds nothing in its
 * hands and is not on a fluid-walking mount.
 *
 * <p>{@link #STANDING} is the case every state's {@link me.whereareiam.anvil.protocol.java.block.model.BlockState#getCollision()
 * collision} holds for; a state lists only the cases whose shape differs from it.</p>
 *
 * <pre>{@code
 * CollisionCase now = new CollisionCase(EntityBottom.ABOVE, sneaking, fallDistance > 2.5, wearsLeatherBoots);
 * CollisionShape shape = state.collision(now);
 * }</pre>
 */
@Value
public class CollisionCase {
	/**
	 * A player standing on the block: above it, neither descending nor falling, without leather boots.
	 */
	public static final @NotNull CollisionCase STANDING = new CollisionCase(EntityBottom.ABOVE, false, false, false);

	/**
	 * Where the player's feet are relative to the block.
	 */
	@NotNull EntityBottom bottom;
	/**
	 * Whether the player descends ({@code Entity.isDescending}, holding sneak).
	 */
	boolean descending;
	/**
	 * Whether the player has fallen more than {@code 2.5} blocks, as powder snow asks ({@code fallDistance > 2.5F}).
	 */
	boolean falling;
	/**
	 * Whether the player wears leather boots, on which it walks on powder snow.
	 */
	boolean leatherBoots;
}
