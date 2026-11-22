package me.whereareiam.anvil.testkit.physics.harness;

import lombok.Value;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One course of {@code anvil-testkit/physics/courses}: the blocks laid around the player, how the player comes into
 * the level, which keys it holds on which ticks and what the server sends it on which ticks. Everything a course does
 * not give is vanilla's default for a fresh survival player on its first tick in that release's normal overworld.
 */
@Value
public class Course {
	/**
	 * The course's name, its file name without {@code .toml}.
	 */
	String name;

	/**
	 * What the course exercises.
	 */
	String description;

	/**
	 * The first Minecraft version the course runs on, such as {@code 1.21.2} for a teleport that sets the player's
	 * velocity, or {@code null} when it runs on every release.
	 */
	String since;

	/**
	 * How many ticks to run, from the first tick after the player is placed or joined.
	 */
	int ticks;

	/**
	 * Where the player is placed, at rest, without the server's handshake; {@code null} when it joins.
	 */
	Start start;

	/**
	 * How the player joins as over the network; {@code null} when it is placed.
	 */
	Join join;

	/**
	 * The boxes of blocks, laid in order, so a later box replaces what an earlier one laid.
	 */
	List<Box> blocks;

	/**
	 * The key changes, ordered by tick; each holds its keys until the next.
	 */
	List<KeyChange> keys;

	/**
	 * What the server sends, ordered by tick; a join's handshake is tick {@code -1}.
	 */
	List<ServerPacket> server;

	/**
	 * Returns the keys held on {@code tick}: those of the last change at or before it, none before the first.
	 *
	 * @param tick the 0-based tick
	 * @return the held keys
	 */
	public Set<CourseKey> heldOn(int tick) {
		Set<CourseKey> held = Set.of();
		for (KeyChange change : keys) {
			if (change.getTick() > tick)
				break;
			held = change.getHeld();
		}
		return held;
	}

	/**
	 * Returns what the server sends before {@code tick} runs, in the course's order.
	 *
	 * @param tick the 0-based tick
	 * @return the server's packets
	 */
	public List<ServerPacket> serverBefore(int tick) {
		return server.stream().filter(packet -> packet.getTick() == tick).collect(Collectors.toList());
	}

	/**
	 * Returns the pings the server sends during a join's handshake, after the login and before the first position
	 * packet, in the course's order, as an anticheat pings before each teleport.
	 *
	 * @return the handshake's pings
	 */
	public List<Ping> handshake() {
		return server.stream().filter(packet -> packet.getTick() == Join.HANDSHAKE).map(Ping.class::cast)
			.collect(Collectors.toList());
	}

	/**
	 * Whether the course runs on a release.
	 *
	 * @param minecraft the release's Minecraft version
	 * @return whether it is at least {@link #since}
	 */
	public boolean runsOn(String minecraft) {
		return since == null || MinecraftVersions.compare(minecraft, since) >= 0;
	}

	/**
	 * The radius in chunks of the square of columns around the origin a course's level loads: two columns beyond the
	 * farthest block, start, join position or absolute teleport target.
	 *
	 * @return the radius
	 */
	public int chunkRadius() {
		int reach = 0;
		if (start != null)
			reach = reach(reach, start.getX(), start.getZ());
		if (join != null)
			reach = reach(reach, join.getX(), join.getZ());
		for (ServerPacket packet : server) {
			if (!(packet instanceof Teleport))
				continue;
			Teleport teleport = (Teleport) packet;
			if (!teleport.getRelatives().contains(CourseRelative.X) && !teleport.getRelatives().contains(CourseRelative.Z))
				reach = reach(reach, teleport.getX(), teleport.getZ());
		}
		for (Box box : blocks)
			reach = Math.max(reach, Math.max(Math.max(Math.abs(box.getMinX()), Math.abs(box.getMaxX())),
				Math.max(Math.abs(box.getMinZ()), Math.abs(box.getMaxZ()))));
		return (reach >> 4) + 2;
	}

	private static int reach(int reach, double x, double z) {
		return Math.max(reach, Math.max(Math.abs((int) Math.floor(x)), Math.abs((int) Math.floor(z))));
	}

	/**
	 * The player's starting position and view.
	 */
	@Value
	public static class Start {
		double x;
		double y;
		double z;
		float yaw;
		float pitch;
	}

	/**
	 * A join as over the network: the server sends the login, then the first position packet, teleport ID
	 * {@value #TELEPORT_ID}, to the join's position and view, then, after {@link #chunksAfterTicks} client ticks, the
	 * course's chunks.
	 */
	@Value
	public static class Join {
		/**
		 * The ID of the first position packet's teleport, which a server's first teleport of a connection carries.
		 */
		public static final int TELEPORT_ID = 1;
		/**
		 * The tick of what the server sends during the handshake, as traces write it.
		 */
		public static final int HANDSHAKE = -1;

		double x;
		double y;
		double z;
		float yaw;
		float pitch;
		int chunksAfterTicks;

		/**
		 * Returns the first position packet, teleport ID {@value #TELEPORT_ID}, to the join's position and view.
		 *
		 * @return the handshake's teleport
		 */
		public Teleport teleport() {
			return new Teleport(HANDSHAKE, TELEPORT_ID, x, y, z, yaw, pitch, 0.0, 0.0, 0.0, Set.of());
		}
	}

	/**
	 * A box of one block state, both corners included.
	 */
	@Value
	public static class Box {
		int minX;
		int minY;
		int minZ;
		int maxX;
		int maxY;
		int maxZ;

		/**
		 * The block state as vanilla writes it, such as {@code minecraft:oak_stairs[facing=north,half=bottom]}.
		 */
		BlockStateText state;
	}

	/**
	 * The keys held from one tick on.
	 */
	@Value
	public static class KeyChange {
		int tick;
		Set<CourseKey> held;
	}

	/**
	 * A packet the server sends before a tick runs, or during a join's handshake.
	 */
	public interface ServerPacket {
		/**
		 * Returns the tick the packet comes before, {@link Join#HANDSHAKE} during a join's handshake.
		 *
		 * @return the tick
		 */
		int getTick();

		/**
		 * Hands the packet to a running course's client through the client's own handler.
		 *
		 * @param run the running course
		 */
		void deliver(VanillaClient.CourseRun run);
	}

	/**
	 * A position packet: the server places the player, turns its view and, from Minecraft 1.21.2, sets its velocity, each
	 * coordinate, angle and velocity component named in {@link #relatives} added to the current one.
	 */
	@Value
	public static class Teleport implements ServerPacket {
		int tick;
		int id;
		double x;
		double y;
		double z;
		float yaw;
		float pitch;
		double dx;
		double dy;
		double dz;
		Set<CourseRelative> relatives;

		/**
		 * Whether the teleport says something only releases from Minecraft 1.21.2 carry: a velocity, or a velocity
		 * component or rotation of it relative.
		 */
		public boolean needsVelocity() {
			return dx != 0.0 || dy != 0.0 || dz != 0.0 || relatives.stream().anyMatch(CourseRelative::isVelocity);
		}

		@Override
		public void deliver(VanillaClient.CourseRun run) {
			run.teleport(this);
		}
	}

	/**
	 * A ping the client answers with its ID: from Minecraft 1.17 the ping packet, before it a rejected transaction of
	 * the player's inventory, which servers send a client of those releases to the same end. Its ID fits the
	 * transaction's action number, a short.
	 */
	@Value
	public static class Ping implements ServerPacket {
		int tick;
		short id;

		@Override
		public void deliver(VanillaClient.CourseRun run) {
			run.ping(this);
		}
	}

	/**
	 * An animation the server plays of the player's own entity, such as a swing of one of its arms, which the client of
	 * a release before Minecraft 26.3 swings back and so tells the server.
	 */
	@Value
	public static class Animate implements ServerPacket {
		int tick;
		CourseAnimation animation;

		@Override
		public void deliver(VanillaClient.CourseRun run) {
			run.animate(this);
		}
	}
}
