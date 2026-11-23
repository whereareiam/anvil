package me.whereareiam.anvil.edition.java.physics.living;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.edition.java.physics.EntityRules;
import me.whereareiam.anvil.edition.java.physics.entity.EntityBody;
import me.whereareiam.anvil.edition.java.physics.entity.EntityDimensions;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.edition.java.physics.living.attribute.LivingAttributes;
import me.whereareiam.anvil.edition.java.physics.living.effect.Effects;
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
public class LivingBody extends EntityBody {
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
	 * The speed the entity's controls set ({@code LivingEntity.speed}); {@link #speed} reads it.
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
	public void sprinting(boolean sprinting) {
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
	 * Tells whether the entity cannot act ({@code LivingEntity.isImmobile}), as a dead entity cannot: it then neither
	 * jumps nor has input.
	 *
	 * @return {@code false} by default
	 */
	public boolean immobile() {
		return false;
	}

	/**
	 * The speed the entity walks at on the ground ({@code LivingEntity.getSpeed}), which {@code Player} reads from its
	 * movement speed attribute.
	 *
	 * @return the speed its controls set
	 */
	public float speed() {
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
	public boolean affectedByFluids() {
		return true;
	}

	/**
	 * Tells whether the entity climbs where it is ({@code LivingEntity.onClimbable}): it does not spectate and is in a
	 * climbable block ({@link Climbing#isClimbable}); a flying player does not climb.
	 *
	 * @return whether it climbs
	 */
	public boolean onClimbable() {
		return !spectator() && Climbing.isClimbable(level(), blockPosition());
	}

	/**
	 * Tells whether the entity holds on to what it climbs instead of sliding down, which vanilla lets only a player do
	 * while its shift key is down ({@code isSuppressingSlidingDownLadder} in {@code handleOnClimbable}).
	 *
	 * @return {@code false} by default
	 */
	public boolean suppressingSlidingDownLadder() {
		return false;
	}
}
