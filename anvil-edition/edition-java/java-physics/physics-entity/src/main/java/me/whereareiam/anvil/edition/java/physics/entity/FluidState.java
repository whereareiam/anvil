package me.whereareiam.anvil.edition.java.physics.entity;

import lombok.Getter;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.physics.world.Fluids;
import org.jetbrains.annotations.NotNull;

/**
 * How an entity is in water and lava, as {@code Entity.baseTick} measures it each tick: whether it touches water and
 * how deep it is in each fluid ({@code updateFluidHeightAndDoFluidPushing}), which also pushes it along the flow where
 * the fluid pushes it ({@link EntityBody#pushedByFluid}), and whether its eyes are in water
 * ({@code updateFluidOnEyes}); in lava its fall distance halves.
 */
public final class FluidState {
	private static final double WATER_PUSH = 0.014;
	private static final double LAVA_PUSH = 0.0023333333333333335;
	private static final double FAST_LAVA_PUSH = 0.007;
	private static final double MIN_PUSH = 0.0045000000000000005;

	private final @NotNull EntityBody body;
	private final @NotNull BlockReader reader;
	private final @NotNull Fluids fluids;
	@Getter
	private boolean touchingWater;
	@Getter
	private boolean eyeInWater;
	@Getter
	private double waterHeight;
	@Getter
	private double lavaHeight;

	/**
	 * Creates the fluid state of an entity.
	 *
	 * @param body the entity
	 */
	public FluidState(@NotNull EntityBody body) {
		this.body = body;
		this.reader = body.level();
		this.fluids = new Fluids(reader);
	}

	/**
	 * Measures the fluids of {@code Entity.baseTick}: water, lava and the eyes.
	 */
	public void baseTick() {
		waterHeight = 0.0;
		lavaHeight = 0.0;
		updateWater();
		Contact lava = contact(FluidType.LAVA, reader.fastLava() ? FAST_LAVA_PUSH : LAVA_PUSH);
		if (lava != null) lavaHeight = lava.depth();
		updateEyes();
		if (isInLava()) body.fallDistance(body.has(EntityBehaviour.DOUBLE_FALL_DISTANCE)
				? body.fallDistance() * 0.5
				: (float) body.fallDistance() * 0.5F);
	}

	/**
	 * Updates whether the entity touches water and pushes it along the current
	 * ({@code updateInWaterStateAndDoWaterCurrentPushing}); touching water resets the fall distance.
	 */
	public void updateWater() {
		Contact contact = contact(FluidType.WATER, WATER_PUSH);
		if (contact != null) waterHeight = contact.depth();
		touchingWater = contact != null && contact.touching();
		if (touchingWater) body.resetFallDistance();
	}

	/**
	 * Tells whether the entity is in lava ({@code Entity.isInLava}), never in its first tick.
	 *
	 * @return whether it is in lava
	 */
	public boolean isInLava() {
		return !body.firstTick() && lavaHeight > 0.0;
	}

	/**
	 * The depth from which the entity swims instead of jumping off the ground ({@code getFluidJumpThreshold}).
	 *
	 * @return the threshold
	 */
	public double fluidJumpThreshold() {
		return body.dimensions().eyeHeight() < 0.4 ? 0.0 : 0.4;
	}

	/**
	 * Measures how deep the entity is in a fluid and pushes it along the fluid's flow; in a box touching an unloaded
	 * column nothing changes.
	 *
	 * @return whether the entity touches the fluid and how deep it is in it, or {@code null} for an unloaded column
	 */
	private Contact contact(@NotNull FluidType type, double pushScale) {
		Box around = body.box().inflate(1.0);
		if (!reader.hasChunksAt(Coordinates.floor(around.minX), Coordinates.floor(around.minZ),
				Coordinates.ceil(around.maxX), Coordinates.ceil(around.maxZ))) return null;

		Box inner = body.box().deflate(0.001);
		Flow flow = new Flow();
		for (int x = Coordinates.floor(inner.minX); x < Coordinates.ceil(inner.maxX); x++)
			for (int y = Coordinates.floor(inner.minY); y < Coordinates.ceil(inner.maxY); y++)
				for (int z = Coordinates.floor(inner.minZ); z < Coordinates.ceil(inner.maxZ); z++)
					if (fluids.type(x, y, z) == type) flow.take(new int[] {x, y, z}, inner.minY);

		if (body.pushedByFluid()) push(flow, pushScale);
		return new Contact(flow.touching, flow.depth);
	}

	private void push(@NotNull Flow flow, double pushScale) {
		if (!(flow.push.length() > 0.0)) return;

		Vector push = flow.push;
		if (flow.pushes > 0) push = push.scale(1.0 / flow.pushes);
		push = push.scale(pushScale);
		Vector delta = body.deltaMovement();
		if (Math.abs(delta.x) < 0.003 && Math.abs(delta.z) < 0.003 && push.length() < MIN_PUSH)
			push = push.normalize().scale(MIN_PUSH);
		body.deltaMovement(delta.add(push));
	}

	private void updateEyes() {
		eyeInWater = false;
		double offset = body.has(EntityBehaviour.LOWERED_EYE_FLUID_TEST) ? 0.11111111F : 0.0;
		double eyes = body.position().y + body.dimensions().eyeHeight() - offset;
		int x = Coordinates.floor(body.position().x);
		int y = Coordinates.floor(eyes);
		int z = Coordinates.floor(body.position().z);
		if (fluids.type(x, y, z) != FluidType.WATER) return;

		eyeInWater = y + fluids.height(x, y, z) > eyes;
	}

	/**
	 * The fluid blocks a box touches, with the deepest reach and their summed flow.
	 */
	private final class Flow {
		private boolean touching;
		private double depth;
		private @NotNull Vector push = Vector.ZERO;
		private int pushes;

		private void take(int @NotNull [] block, double bottom) {
			double surface = block[1] + fluids.height(block[0], block[1], block[2]);
			if (surface < bottom) return;

			touching = true;
			depth = Math.max(surface - bottom, depth);
			if (!body.pushedByFluid()) return;

			Vector direction = fluids.flow(block[0], block[1], block[2]);
			push = push.add(depth < 0.4 ? direction.scale(depth) : direction);
			pushes++;
		}
	}

	/**
	 * Whether the entity touches a fluid and how deep it is in it.
	 */
	private record Contact(boolean touching, double depth) {
	}
}
