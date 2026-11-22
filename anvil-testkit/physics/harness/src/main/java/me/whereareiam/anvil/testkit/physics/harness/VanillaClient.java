package me.whereareiam.anvil.testkit.physics.harness;

import me.whereareiam.anvil.testkit.physics.harness.block.BlockProbe;

import java.util.List;
import java.util.Set;

/**
 * The real vanilla client of one version family, set up headless: what each family's harness implements against the
 * client classes of its releases, so the shared {@link VanillaHarness} reads every output the same way from each.
 */
public interface VanillaClient {
	/**
	 * Bootstraps the game and loads the registries and tags from the client JAR's own vanilla data pack.
	 *
	 * @return the Minecraft version the running client reports, such as {@code 1.21.11}
	 * @throws Exception when the client cannot be set up
	 */
	String bootstrap() throws Exception;

	/**
	 * Starts a course on a fresh connection, every random source the movement reaches seeded with
	 * {@link VanillaHarness#SEED}. A course that {@linkplain Course#getJoin() joins} gets the server's login, the pings
	 * of its {@linkplain Course#handshake() handshake}, its first position packet and, where the release has it, the
	 * game event that starts the level's loading, each through the client's own handler, but none of the course's chunks
	 * yet. A course that is placed gets a level holding the
	 * course's blocks and a player at its start, at rest, as a login leaves it: survival, default abilities and
	 * attributes, full food, its level loaded.
	 *
	 * @param course the course
	 * @return the started course
	 * @throws Exception when the level or player cannot be created
	 */
	CourseRun start(Course course) throws Exception;

	/**
	 * Returns the probe that reads the blocks from the client's own registries, classes and methods, in a fresh level
	 * of its own with a player of its own.
	 *
	 * @return the probe
	 * @throws Exception when the level or player cannot be created
	 */
	BlockProbe<?, ?> blocks() throws Exception;

	/**
	 * A course running on the client.
	 */
	interface CourseRun {
		/**
		 * Holds exactly {@code keys} for the next tick.
		 *
		 * @param keys the held keys
		 */
		void hold(Set<CourseKey> keys);

		/**
		 * Hands the client the course's chunks as the server's chunk packets, through the client's own handler.
		 */
		void loadChunks();

		/**
		 * Hands the client a position packet of the server through the client's own handler.
		 *
		 * @param teleport the teleport
		 */
		void teleport(Course.Teleport teleport);

		/**
		 * Hands the client a ping of the server through the client's own handler: from Minecraft 1.17 the ping packet,
		 * before it a rejected transaction of the player's inventory, which the client answers alike.
		 *
		 * @param ping the ping
		 */
		void ping(Course.Ping ping);

		/**
		 * Hands the client an animation the server plays of its player's own entity through the client's own handler,
		 * the animate packet built by the release from the client's player.
		 *
		 * @param animate the animation
		 */
		void animate(Course.Animate animate);

		/**
		 * Runs one client tick, as {@code Minecraft.tick} runs what concerns the player: the game mode's tick, which
		 * ticks the connection's listener, the level's entities, what the release runs for the player after them, and
		 * the end of the client tick where the release sends it.
		 */
		void tick();

		/**
		 * Returns what the client's player is left with now.
		 */
		TickSample sample();

		/**
		 * Returns the packets the client sent since the last call, in send order.
		 */
		List<PacketLine> sent();
	}
}
