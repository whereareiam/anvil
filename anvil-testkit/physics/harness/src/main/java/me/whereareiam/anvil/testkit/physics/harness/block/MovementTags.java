package me.whereareiam.anvil.testkit.physics.harness.block;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The block tags vanilla's movement code reads, whose members the block data records per release: a release that has
 * no such tag records no member of it.
 */
public final class MovementTags {
	/**
	 * The tags, each named by the vanilla code that reads it:
	 * <ul>
	 *     <li>{@code minecraft:climbable}: {@code LivingEntity.onClimbable}, an entity in the block climbs;</li>
	 *     <li>{@code minecraft:fences} and {@code minecraft:walls}: {@code Entity.getOnPos}, which keeps such a block
	 *     below an entity as the block it stands on;</li>
	 *     <li>{@code minecraft:soul_speed_blocks}: {@code LivingEntity.onSoulSpeedBlock}, the blocks soul speed
	 *     quickens;</li>
	 *     <li>{@code minecraft:can_glide_through}: the blocks a gliding entity keeps gliding in;</li>
	 *     <li>{@code minecraft:suppresses_bounce}: {@code Entity.restituteMovementAfterCollisions} from 26.2, on which
	 *     nothing bounces;</li>
	 *     <li>{@code minecraft:blocks_fluid_flow}: {@code FlowingFluid.getFlow} from 26.3, beside which a fluid does
	 *     not flow.</li>
	 * </ul>
	 */
	public static final Set<String> NAMES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
		"minecraft:climbable",
		"minecraft:fences",
		"minecraft:walls",
		"minecraft:soul_speed_blocks",
		"minecraft:can_glide_through",
		"minecraft:suppresses_bounce",
		"minecraft:blocks_fluid_flow"
	)));

	private MovementTags() {
	}
}
