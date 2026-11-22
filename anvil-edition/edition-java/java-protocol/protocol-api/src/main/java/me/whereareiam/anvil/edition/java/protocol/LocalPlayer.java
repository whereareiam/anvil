package me.whereareiam.anvil.edition.java.protocol;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The client's own player of one simulated player, as the vanilla client keeps it ({@code LocalPlayer}): where the
 * client knows itself to be, its view, velocity and ground state, what the server set of its own entity, and the
 * client's tick, which runs 20 times a second. Every player that speaks the Java Edition protocol has it, whatever
 * features it installs: it accepts each server teleport with the position and view it results in and, before 26.3,
 * follows the acceptance with them ({@code handleMovePlayer}), takes the velocity the server gives its own entity, tells
 * the server once it has loaded its level from Minecraft 1.21.4 ({@code ServerboundPlayerLoaded}) and ends each tick
 * from 1.21.2 ({@code ServerboundClientTickEnd}). A login, a respawn and a closed connection make a new player, which
 * knows no position and waits for its level again.
 *
 * <p>The player does not move by itself: a feature that simulates it, such as client physics, runs its own tick of the
 * player {@link #onTick(Runnable) within the client's tick} and moves it with the setters.</p>
 *
 * <p>The client applies every packet it receives and runs every tick under one lock, as the vanilla client runs both
 * on its own thread, so packet listeners and ticks never overlap. Each call of this interface takes the lock and sees
 * one moment; {@link #run(Runnable)} and {@link #get(Supplier)} hold it for several calls, such as an operation that
 * reads the position and moves the player.</p>
 *
 * <pre>{@code
 * LocalPlayer client = player.protocol(JavaProtocol.class).player();
 * Subscription ticks = client.onTick(() -> client.position().ifPresent(at ->
 *         client.position(new Vec3d(at.getX(), at.getY() - 0.1, at.getZ()))));
 * }</pre>
 */
public interface LocalPlayer {
	/**
	 * Returns where the client knows its player is: the position of its feet.
	 *
	 * @return the position, or nothing before a server teleport or a feature placed the player on its current
	 * connection
	 */
	@NotNull Optional<Vec3d> position();

	/**
	 * Places the player, such as where a feature's tick moved it. The client does not tell the server; the feature that
	 * moves the player sends what the vanilla client would.
	 *
	 * @param position the position of the player's feet
	 */
	void position(@NotNull Vec3d position);

	/**
	 * Returns where the player looks.
	 *
	 * @return the view's yaw and pitch, both zero for a new player
	 */
	@NotNull ViewRotation rotation();

	/**
	 * Turns the player's view, without telling the server.
	 *
	 * @param rotation the view's yaw and pitch
	 */
	void rotation(@NotNull ViewRotation rotation);

	/**
	 * Tells whether the player stands on the ground, as the client last decided or a feature set it.
	 *
	 * @return whether it is on the ground
	 */
	boolean onGround();

	/**
	 * Sets whether the player stands on the ground.
	 *
	 * @param onGround whether it is on the ground
	 */
	void onGround(boolean onGround);

	/**
	 * Returns the player's velocity ({@code deltaMovement}): what a teleport left it, what the server gave the player's
	 * own entity since, or what a feature's tick set.
	 *
	 * @return the velocity in blocks per tick, zero for a new player
	 */
	@NotNull Vec3d velocity();

	/**
	 * Sets the player's velocity, such as where a feature's tick left it.
	 *
	 * @param velocity the velocity in blocks per tick
	 */
	void velocity(@NotNull Vec3d velocity);

	/**
	 * Returns the protocol ID of the player's own entity, which the server may choose negative.
	 *
	 * @return the ID the login named, or nothing until the player joined a level on its current connection
	 */
	@NotNull Optional<EntityId> entityId();

	/**
	 * Returns the player's game mode, as the login, each respawn and each change of the game mode set it.
	 *
	 * @return the game mode, survival until the server names another
	 */
	@NotNull GameMode gameMode();

	/**
	 * Tells whether the player is dead: from a health of zero or less, or the combat kill of its own entity, until it
	 * respawns.
	 *
	 * @return whether it is dead
	 */
	boolean isDead();

	/**
	 * Runs an action under the client's lock, between ticks and packets, so it sees and changes the player at one
	 * moment. The action must not wait for packets or ticks of the player, which cannot run until it returns.
	 *
	 * @param action what reads or changes the player
	 */
	void run(@NotNull Runnable action);

	/**
	 * Reads or changes the player under the client's lock, between ticks and packets.
	 *
	 * @param reading what reads or changes the player and answers
	 * @param <T> what it answers
	 * @return its answer
	 */
	<T> T get(@NotNull Supplier<? extends T> reading);

	/**
	 * Runs an action in each tick of the client in which the client ticks its player ({@code LocalPlayer.tick}): after
	 * the connection's tick and before the end of the client's tick, under the client's lock, while the player has
	 * joined a level, its position is known and the client has loaded its level, and before Minecraft 1.17 only while
	 * the chunk the player is in is loaded. An action that throws is reported on standard error and does not keep the
	 * client or the other actions from ticking.
	 *
	 * @param action the feature's own tick of the player
	 * @return registration that stops running the action when closed
	 */
	@NotNull Subscription onTick(@NotNull Runnable action);
}
