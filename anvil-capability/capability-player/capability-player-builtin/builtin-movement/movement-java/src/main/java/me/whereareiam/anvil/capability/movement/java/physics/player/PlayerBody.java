package me.whereareiam.anvil.capability.movement.java.physics.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.MovementRules;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.edition.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.living.LivingBody;
import me.whereareiam.anvil.edition.java.physics.living.attribute.LivingAttributes;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The vanilla client's own player as {@code Player} and {@code LocalPlayer} extend a living entity: what the server set
 * of it, the keys it holds, whether it crouches for them and whether its eyes were under water when the tick started,
 * and the flight changes the client told the server during the current tick. It answers the kit's hooks as those
 * classes override them: its shift key is its sneak key, a spectator moves through blocks and does not climb, a flying
 * player is neither pushed nor slowed by fluids and does not climb, its speeds come from its attributes and abilities,
 * a sneaking player backs off edges ({@link EdgeBackOff}), and a dead player cannot act.
 *
 * <p>Not thread-safe; its owner serializes ticks and placements.</p>
 */
@Getter
@Setter
public final class PlayerBody extends LivingBody {
	@Setter(AccessLevel.NONE)
	private final @NotNull MovementRules client;
	/**
	 * What the server set of the player.
	 */
	@Setter(AccessLevel.NONE)
	private final @NotNull PlayerState server;
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final @NotNull EdgeBackOff edge;
	/**
	 * The keys the client holds this tick ({@code input.keyPresses}).
	 */
	private @NotNull MovementInput keys = MovementInput.NONE;
	/**
	 * Whether the client crouches for its input ({@code LocalPlayer.crouching}), which may differ from its pose.
	 */
	private boolean crouching;
	/**
	 * Whether the player's eyes were in water at the start of the tick ({@code Player.wasUnderwater}).
	 */
	private boolean underWater;
	/**
	 * The air control speed of the clients before 1.19.4, which {@code Player.aiStep} keeps for the next tick.
	 */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private float legacyFlyingSpeed = 0.02F;
	/**
	 * The abilities the client told the server during the current tick, oldest first.
	 */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final @NotNull List<ServerboundPlayerAbilities> abilityUpdates = new ArrayList<>();

	/**
	 * Creates the body of a standing player at the world's origin.
	 *
	 * @param client the client's rules
	 * @param level the blocks its client knows
	 * @param server what the server set of the player, whose attributes and effects the player has
	 */
	public PlayerBody(@NotNull MovementRules client, @NotNull BlockReader level, @NotNull PlayerState server) {
		super(client.entity(), level, Pose.STANDING, server.attributes(), server.effects());
		this.client = client;
		this.server = server;
		this.edge = new EdgeBackOff(this);
	}

	/**
	 * Tells whether the client behaves so.
	 *
	 * @param behaviour the behaviour
	 * @return whether the client's version has it
	 */
	public boolean has(@NotNull ClientBehaviour behaviour) {
		return client.has(behaviour);
	}

	/**
	 * Tells the server whether the player flies, as {@code LocalPlayer.onUpdateAbilities} does each time the client
	 * starts or stops flying on its own; the packet leaves with the packets of the tick.
	 */
	public void updateAbilities() {
		abilityUpdates.add(new ServerboundPlayerAbilities(server.flying()));
	}

	/**
	 * Returns the abilities the client told the server during the current tick, in the order it told them.
	 *
	 * @return the abilities packets, unmodifiable
	 */
	public @NotNull List<ServerboundPlayerAbilities> abilityUpdates() {
		return Collections.unmodifiableList(abilityUpdates);
	}

	/**
	 * Forgets the abilities told during the previous tick, as a new tick starts.
	 */
	public void startTick() {
		abilityUpdates.clear();
	}

	/**
	 * Keeps the air control speed for the next tick, as {@code Player.aiStep} sets it before 1.19.4.
	 */
	public void rememberFlyingSpeed() {
		legacyFlyingSpeed = sprinting() ? 0.02F + 0.006F : 0.02F;
	}

	@Override
	public boolean shiftKeyDown() {
		return keys.sneak();
	}

	@Override
	public boolean spectator() {
		return server.isSpectator();
	}

	@Override
	public boolean pushedByFluid() {
		return !server.flying();
	}

	@Override
	public boolean suppressingBounce() {
		return shiftKeyDown();
	}

	@Override
	public @NotNull Vector maybeBackOffFromEdge(@NotNull Vector movement) {
		return edge.backOff(movement, maxUpStep());
	}

	/**
	 * {@code LocalPlayer.isHorizontalCollisionMinor}: from 1.18 the player runs almost along the wall it touches.
	 */
	@Override
	public boolean isHorizontalCollisionMinor(@NotNull Vector movement) {
		if (!client.has(ClientBehaviour.MINOR_HORIZONTAL_COLLISION)) return false;

		Trigonometry trigonometry = rules().trigonometry();
		float radians = yaw() * (float) (Math.PI / 180.0);
		double sin = trigonometry.sin(radians);
		double cos = trigonometry.cos(radians);
		double inputX = xxa() * cos - zza() * sin;
		double inputZ = zza() * cos + xxa() * sin;
		double inputSqr = inputX * inputX + inputZ * inputZ;
		double movementSqr = movement.x * movement.x + movement.z * movement.z;
		if (inputSqr < 1.0E-5F || movementSqr < 1.0E-5F) return false;

		double dot = inputX * movement.x + inputZ * movement.z;
		return Math.acos(dot / Math.sqrt(inputSqr * movementSqr)) < 0.13962634F;
	}

	@Override
	public boolean immobile() {
		return server.dead();
	}

	/**
	 * {@code Player.getSpeed}: the movement speed attribute at this moment.
	 */
	@Override
	public float speed() {
		return (float) attributes().value(LivingAttributes.MOVEMENT_SPEED);
	}

	/**
	 * {@code Player.getFlyingSpeed}: the abilities' flying speed while it flies, doubled while it sprints; otherwise
	 * {@code 0.02F}, raised while it sprints, as the current tick decides it from 1.19.4 and the previous one before.
	 */
	@Override
	public float flyingSpeed() {
		if (server.flying()) return sprinting() ? server.flyingSpeed() * 2.0F : server.flyingSpeed();
		if (!client.has(ClientBehaviour.LIVE_FLYING_SPEED)) return legacyFlyingSpeed;

		return sprinting() ? 0.025999999F : 0.02F;
	}

	@Override
	public boolean affectedByFluids() {
		return !server.flying();
	}

	@Override
	public boolean onClimbable() {
		return !server.flying() && super.onClimbable();
	}

	@Override
	public boolean suppressingSlidingDownLadder() {
		return shiftKeyDown();
	}
}
