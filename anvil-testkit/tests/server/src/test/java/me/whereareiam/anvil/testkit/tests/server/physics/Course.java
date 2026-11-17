package me.whereareiam.anvil.testkit.tests.server.physics;

import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One physics course as {@code anvil-testkit/physics/courses/<name>.toml} declares it: the blocks laid in order, where
 * the server places the player at rest, the keys it holds from each tick on, and how many ticks it runs. The reference
 * traces of the vanilla client and this suite run the same files, which {@link CourseReader} reads.
 *
 * <p>A course places its player at its {@code [start]} or, when the player joins, where the join's first position
 * packet puts it, which this suite does alike by teleporting the player there. The packets a course scripts the server
 * to send at given ticks ({@code [[server]]}) belong to the reference harness, whose server is the course itself.</p>
 *
 * <pre>{@code
 * for (Course course : Course.all())
 *     System.out.println(course.getName() + ": " + course.getTicks() + " ticks");
 * }</pre>
 */
@Value
public class Course {
	/**
	 * The system property locating the courses' directory, which each {@code physicsTest-<minecraft>} task sets.
	 */
	public static final String DIRECTORY_PROPERTY = "anvil.physics.courses";

	/**
	 * The course's name: its file name without the extension.
	 */
	@NotNull String name;
	/**
	 * What the player does on the course.
	 */
	@NotNull String description;
	/**
	 * The first Minecraft version the course runs on, or {@code null} when it runs on every supported one.
	 */
	@Nullable MinecraftVersion since;
	/**
	 * How many ticks the course runs, from the first tick after the player is placed.
	 */
	int ticks;
	/**
	 * Where the server places the player, standing at rest.
	 */
	@NotNull Start start;
	/**
	 * The boxes of blocks, laid in order, so a later box wins where two overlap.
	 */
	@NotNull List<Box> blocks;
	/**
	 * The keys the player holds, each from its tick on until the next entry's, earliest first.
	 */
	@NotNull List<Keys> keys;
	/**
	 * Whether the course scripts packets the server sends at given ticks.
	 */
	boolean scriptsServer;

	/**
	 * Reads every course of the directory the {@value #DIRECTORY_PROPERTY} system property names.
	 *
	 * @return the courses, ordered by name
	 * @throws IllegalStateException when the property is not set, or the directory holds no course or an invalid one
	 */
	public static @NotNull List<Course> all() {
		return CourseReader.all(DIRECTORY_PROPERTY);
	}

	/**
	 * Tells whether the course runs on a release: it is at least the course's {@link #getSince()}.
	 *
	 * @param version the release's Minecraft version
	 * @return whether the release runs the course
	 */
	public boolean runsOn(@NotNull MinecraftVersion version) {
		return since == null || version.isAtLeast(since);
	}

	/**
	 * Returns the smallest box holding every block of the course and the block the player starts in.
	 *
	 * @return the course's bounds
	 */
	public @NotNull Box bounds() {
		int[] min = {(int) Math.floor(start.x()), (int) Math.floor(start.y()), (int) Math.floor(start.z())};
		int[] max = min.clone();
		for (Box box : blocks)
			for (int axis = 0; axis < 3; axis++) {
				min[axis] = Math.min(min[axis], box.min(axis));
				max[axis] = Math.max(max[axis], box.max(axis));
			}

		return new Box(min, max, "minecraft:air");
	}

	/**
	 * Returns the block state the course leaves at a position: the last box holding it, or air.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the block state as vanilla writes it
	 */
	public @NotNull String stateAt(int x, int y, int z) {
		for (Box box : blocks.reversed())
			if (box.contains(x, y, z)) return box.state();

		return "minecraft:air";
	}

	@Override
	public String toString() {
		return name;
	}

	/**
	 * Where the player starts: the position of its feet and its view, which stays as given.
	 *
	 * @param x east-west coordinate
	 * @param y height of the feet
	 * @param z north-south coordinate
	 * @param yaw horizontal view direction in degrees
	 * @param pitch vertical view direction in degrees
	 */
	public record Start(double x, double y, double z, float yaw, float pitch) {
	}

	/**
	 * A box of one block state, both corners included.
	 *
	 * @param from one corner
	 * @param to the opposite corner
	 * @param state the block state as vanilla writes it, such as {@code minecraft:stone}
	 */
	public record Box(int @NotNull [] from, int @NotNull [] to, @NotNull String state) {
		/**
		 * Returns the box's lowest coordinate along an axis.
		 *
		 * @param axis 0 for x, 1 for y, 2 for z
		 * @return the coordinate
		 */
		public int min(int axis) {
			return Math.min(from[axis], to[axis]);
		}

		/**
		 * Returns the box's highest coordinate along an axis.
		 *
		 * @param axis 0 for x, 1 for y, 2 for z
		 * @return the coordinate
		 */
		public int max(int axis) {
			return Math.max(from[axis], to[axis]);
		}

		/**
		 * Tells whether the box holds a block.
		 *
		 * @param x the block's east-west coordinate
		 * @param y the block's height
		 * @param z the block's north-south coordinate
		 * @return whether the position lies inside, its faces included
		 */
		public boolean contains(int x, int y, int z) {
			return x >= min(0) && x <= max(0) && y >= min(1) && y <= max(1) && z >= min(2) && z <= max(2);
		}

		/**
		 * Returns the box grown by a margin on each side.
		 *
		 * @param horizontal blocks added east, west, north and south
		 * @param below blocks added below
		 * @param above blocks added above
		 * @return the grown box, of the same state
		 */
		public @NotNull Box grown(int horizontal, int below, int above) {
			return new Box(new int[]{min(0) - horizontal, min(1) - below, min(2) - horizontal},
					new int[]{max(0) + horizontal, max(1) + above, max(2) + horizontal}, state);
		}
	}

	/**
	 * The keys held from a tick on.
	 *
	 * @param tick the first tick, 0-based
	 * @param input the held keys
	 */
	public record Keys(int tick, @NotNull MovementInput input) {
	}
}
