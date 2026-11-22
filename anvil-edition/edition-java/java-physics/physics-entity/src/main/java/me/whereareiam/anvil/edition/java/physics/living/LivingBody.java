package me.whereareiam.anvil.edition.java.physics.living;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.edition.java.physics.EntityRules;
import me.whereareiam.anvil.edition.java.physics.entity.Bounciness;
import me.whereareiam.anvil.edition.java.physics.entity.EntityBody;
import me.whereareiam.anvil.edition.java.physics.entity.EntityDimensions;
import me.whereareiam.anvil.edition.java.physics.living.attribute.Attribute;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.edition.java.physics.living.attribute.LivingAttributes;
import me.whereareiam.anvil.edition.java.physics.living.effect.Effects;
import me.whereareiam.anvil.edition.java.physics.living.effect.MobEffect;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import org.jetbrains.annotations.NotNull;

/**
 * The state of a living entity that its physics shares, as vanilla's {@code LivingEntity} adds it to an
 * {@link EntityBody}: its attributes and effects, whether it sprints and jumps, and its strafing and forward input.
 * Like the entity's, its public non-final methods are the points where vanilla's subclasses change how a living entity
 * moves; the defaults are {@code LivingEntity}'s, and a subclass overrides them as {@code Player} does.
 *
 * <p>Not thread-safe; its owner serializes ticks and placements.</p>
 */
@Getter
@Setter
public class LivingBody extends EntityBody implements Bounciness {
	@Setter(AccessLevel.NONE)
	private final @NotNull AttributeMap attributes;
	@Setter(AccessLevel.NONE)
	private final @NotNull Effects effects;
	@Setter(AccessLevel.NONE)
	private boolean sprinting;
	private boolean jumping;
	/**
	 * The strafing input ({@code xxa}).
	 */
	private float xxa;
	/**
	 * The forward input ({@code zza}).
	 */
	private float zza;
	/**
	 * The speed the entity's controls set ({@code LivingEntity.speed}); {@link #getSpeed} reads it.
	 */
	@Getter(AccessLevel.NONE)
	private float speed;

	/**
	 * Creates the body of a living entity at the world's origin.
	 *
	 * @param rules the behaviours of the client's version
	 * @param level the blocks the entity moves among
	 * @param dimensions the entity's size
	 * @param attributes its attributes, which the server's packets may change
	 * @param effects its effects, which the server's packets may change
	 */
	public LivingBody(
			@NotNull EntityRules rules,
			@NotNull BlockReader level,
			@NotNull EntityDimensions dimensions,
			@NotNull AttributeMap attributes,
			@NotNull Effects effects
	) {
		super(rules, level, dimensions);
		this.attributes = attributes;
		this.effects = effects;
	}

	/**
	 * Starts or stops sprinting and its speed modifier ({@code LivingEntity.setSprinting}).
	 *
	 * @param sprinting whether the entity sprints
	 */
	public void setSprinting(boolean sprinting) {
		this.sprinting = sprinting;
		attributes.sprinting(sprinting);
	}

	/**
	 * The step height attribute ({@code LivingEntity.maxUpStep}).
	 *
	 * @return how high the entity steps up
	 */
	@Override
	public float maxUpStep() {
		return (float) attributes.value(LivingAttributes.STEP_HEIGHT);
	}

	/**
	 * The blocks' speed factor eased towards one by the movement efficiency attribute
	 * ({@code LivingEntity.getBlockSpeedFactor}), which is zero before 1.21.
	 *
	 * @param blockFactor the speed factor of the blocks in and below the entity
	 * @return the factor to apply
	 */
	@Override
	public float blockSpeedFactor(float blockFactor) {
		float efficiency = (float) attributes.value(LivingAttributes.MOVEMENT_EFFICIENCY);
		return blockFactor + efficiency * (1.0F - blockFactor);
	}

	/**
	 * {@code LivingEntity.getEffectiveGravity}: the gravity attribute, at most {@code 0.01} while the entity falls with
	 * slow falling.
	 *
	 * @return the gravity
	 */
	@Override
	public double effectiveGravity() {
		double gravity = attributes.value(LivingAttributes.GRAVITY);
		boolean slow = getDeltaMovement().y <= 0.0 && effects.has(MobEffect.SLOW_FALLING);
		return slow ? Math.min(gravity, 0.01) : gravity;
	}

	/**
	 * {@code LivingEntity.getEntityBounciness}: the bounciness attribute, from 26.2.
	 *
	 * @return the share of its speed it keeps, reversed, when it hits a block
	 */
	@Override
	public double entityBounciness() {
		return attributes.value(LivingAttributes.BOUNCINESS);
	}

	/**
	 * {@code Entity.getBlockBounciness}: a living entity takes a block's whole bounce.
	 *
	 * @param restitution the block's bounce restitution
	 * @return the restitution
	 */
	@Override
	public double blockBounciness(float restitution) {
		return restitution;
	}

	/**
	 * {@code LivingEntity.getAirDrag}: {@code 0.98F} modified by the air drag modifier attribute.
	 *
	 * @return the air drag
	 */
	@Override
	public float airDrag() {
		return modifiedFriction(0.98F, LivingAttributes.AIR_DRAG_MODIFIER);
	}

	/**
	 * {@code LivingEntity.computeModifiedFriction}: the share of speed a friction leaves, with the share it takes scaled
	 * by a modifier attribute, from 26.2.
	 *
	 * @param friction the friction
	 * @param modifier the attribute that scales it
	 * @return the modified friction, between zero and one
	 */
	public float modifiedFriction(float friction, @NotNull Attribute modifier) {
		float modified = 1.0F - (1.0F - friction) * (float) attributes.value(modifier);
		return modified < 0.0F ? 0.0F : Math.min(modified, 1.0F);
	}

	/**
	 * Tells whether the entity cannot act ({@code LivingEntity.isImmobile}), as a dead entity cannot: it then neither
	 * jumps nor has input.
	 *
	 * @return {@code false} by default
	 */
	public boolean isImmobile() {
		return false;
	}

	/**
	 * The speed the entity walks at on the ground ({@code LivingEntity.getSpeed}), which {@code Player} reads from its
	 * movement speed attribute.
	 *
	 * @return the speed its controls set
	 */
	public float getSpeed() {
		return speed;
	}

	/**
	 * The speed of the entity's control in the air ({@code LivingEntity.getFlyingSpeed}), which {@code Player} raises
	 * while it sprints or flies.
	 *
	 * @return {@code 0.02F} by default
	 */
	public float flyingSpeed() {
		return 0.02F;
	}

	/**
	 * Tells whether fluids change how the entity travels and jumps ({@code LivingEntity.isAffectedByFluids}); a flying
	 * player is not affected.
	 *
	 * @return {@code true} by default
	 */
	public boolean isAffectedByFluids() {
		return true;
	}

	/**
	 * Tells whether the entity climbs where it is ({@code LivingEntity.onClimbable}): it does not spectate and is in a
	 * climbable block ({@link Climbing#isClimbable}); a flying player does not climb.
	 *
	 * @return whether it climbs
	 */
	public boolean onClimbable() {
		return !isSpectator() && Climbing.isClimbable(getLevel(), blockPosition());
	}

	/**
	 * Tells whether the entity holds on to what it climbs instead of sliding down, which vanilla lets only a player do
	 * while its shift key is down ({@code isSuppressingSlidingDownLadder} in {@code handleOnClimbable}).
	 *
	 * @return {@code false} by default
	 */
	public boolean isSuppressingSlidingDownLadder() {
		return false;
	}
}
