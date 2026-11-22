package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One player a block's collision shape is read for, through {@code CollisionContext.of(player)} as vanilla's entity
 * collision reads it: the inputs of that context a player's movement changes. Where its feet are relative to the
 * block, whether it descends (holds sneak, {@code isDescending}), whether it has fallen far enough for powder snow to
 * hold it ({@link #FALL_DISTANCE}, beyond vanilla's {@code 2.5}), and whether it wears leather boots, on which it walks
 * on powder snow. It holds nothing in its hands and stands in the block's column.
 */
@Value
public class CollisionCase {
	/**
	 * The fall distance of a falling case.
	 */
	public static final double FALL_DISTANCE = 3.0;

	/**
	 * A player standing on the block, the shape a block's data records first: on its top, neither descending nor
	 * falling, without leather boots.
	 */
	public static final CollisionCase STANDING = new CollisionCase(EntityBottom.ABOVE, false, false, false);

	private static final List<CollisionCase> ALL = all();

	/**
	 * Where the player's feet are.
	 */
	EntityBottom bottom;

	/**
	 * Whether the player descends.
	 */
	boolean descending;

	/**
	 * Whether the player has fallen {@link #FALL_DISTANCE}.
	 */
	boolean falling;

	/**
	 * Whether the player wears leather boots.
	 */
	boolean leatherBoots;

	/**
	 * Every case, {@link #STANDING} first.
	 *
	 * @return the cases
	 */
	public static List<CollisionCase> cases() {
		return ALL;
	}

	private static List<CollisionCase> all() {
		List<CollisionCase> cases = new ArrayList<>();
		cases.add(STANDING);
		for (EntityBottom bottom : EntityBottom.values())
			for (boolean descending : new boolean[] {false, true})
				for (boolean falling : new boolean[] {false, true})
					for (boolean boots : new boolean[] {false, true}) {
						CollisionCase next = new CollisionCase(bottom, descending, falling, boots);
						if (!next.equals(STANDING))
							cases.add(next);
					}
		return Collections.unmodifiableList(cases);
	}
}
