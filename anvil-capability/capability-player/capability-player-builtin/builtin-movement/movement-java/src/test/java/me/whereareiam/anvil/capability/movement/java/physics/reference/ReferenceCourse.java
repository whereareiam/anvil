package me.whereareiam.anvil.capability.movement.java.physics.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One course of {@code anvil-testkit/physics/courses} as the vanilla client's reference traces were generated from it:
 * the first release it runs on, how the player comes into the level, the boxes of blocks laid in order, the keys held
 * from each entry's tick on and the teleports, pings and animations of the player the server sends.
 *
 * @param name the course's file name without {@code .toml}
 * @param since the first Minecraft version the course runs on, or {@code null} for every release
 * @param ticks how many ticks the course runs
 * @param start where the player is placed at rest, without the server's handshake, or {@code null} when it joins
 * @param join how the player joins as over the network, or {@code null} when it is placed
 * @param blocks the boxes of blocks, a later one replacing what an earlier one laid
 * @param keys the key entries, ordered by tick
 * @param server the teleports, pings and animations the server sends, ordered by tick, a join's handshake as tick {@code -1}
 */
record ReferenceCourse(
		@NotNull String name,
		@Nullable String since,
		int ticks,
		@Nullable Position start,
		@Nullable Join join,
		@NotNull List<Blocks> blocks,
		@NotNull List<Keys> keys,
		@NotNull List<ServerPacket> server
) {
	private static final int HANDSHAKE = -1;
	private static final Pattern STATE = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\[([a-z0-9_=,]+)])?");
	private static final Set<String> KEYS = Set.of("forward", "backward", "left", "right", "jump", "sneak", "sprint");

	/**
	 * Reads a course file.
	 *
	 * @param file the course's TOML file
	 * @return the course
	 */
	static @NotNull ReferenceCourse read(@NotNull Path file) {
		String fileName = file.getFileName().toString();
		JsonNode root;
		try {
			root = new TomlMapper().readTree(file.toFile());
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read the course " + file, failure);
		}

		List<Blocks> blocks = new ArrayList<>();
		for (JsonNode box : root.path("blocks")) blocks.add(blocks(box));
		List<Keys> keys = new ArrayList<>();
		for (JsonNode entry : root.path("keys")) keys.add(keys(entry));
		List<ServerPacket> server = new ArrayList<>();
		for (JsonNode entry : root.path("server")) server.add(ServerPacket.read(entry));
		JsonNode join = root.path("join");
		return new ReferenceCourse(fileName.substring(0, fileName.length() - ".toml".length()),
				root.has("since") ? root.path("since").asText() : null, root.path("ticks").asInt(),
				root.has("start") ? position(root.path("start")) : null,
				join.isMissingNode() ? null : new Join(position(join), join.path("chunksAfterTicks").asInt()),
				List.copyOf(blocks), List.copyOf(keys), List.copyOf(server));
	}

	/**
	 * Tells whether the course runs on a release.
	 *
	 * @param release the release's Minecraft version
	 * @return whether it is at least {@link #since}
	 */
	boolean runsOn(@NotNull MinecraftVersion release) {
		return since == null || release.isAtLeast(MinecraftVersion.parse(since));
	}

	/**
	 * Returns what the server sends before a tick runs, in the course's order; tick {@code -1} is a join's handshake,
	 * after the login and before the first position packet.
	 *
	 * @param tick the 0-based tick, or {@code -1}
	 * @return the server's packets
	 */
	@NotNull List<ServerPacket> serverBefore(int tick) {
		return server.stream().filter(packet -> packet.tick() == tick).toList();
	}

	/**
	 * Returns what the server sends during a join's handshake, after the login and before the first position packet.
	 *
	 * @return the server's packets
	 */
	@NotNull List<ServerPacket> handshake() {
		return serverBefore(HANDSHAKE);
	}

	private static Position position(JsonNode table) {
		return Position.builder().x(table.path("x").asDouble()).y(table.path("y").asDouble())
				.z(table.path("z").asDouble()).yaw((float) table.path("yaw").asDouble())
				.pitch((float) table.path("pitch").asDouble()).build();
	}

	/**
	 * Returns the keys held on a tick: those of the last entry at or before it, none before the first.
	 *
	 * @param tick the 0-based tick
	 * @return the held keys
	 */
	@NotNull MovementInput heldOn(int tick) {
		MovementInput held = MovementInput.NONE;
		for (Keys entry : keys) {
			if (entry.tick() > tick) break;
			held = entry.input();
		}
		return held;
	}

	/**
	 * Returns the radius in chunks of the square of loaded columns around the origin that the vanilla harness gives a
	 * course: two columns beyond the farthest block, start, join position or absolute teleport target.
	 *
	 * @return the radius
	 */
	int chunkRadius() {
		int reach = 0;
		if (start != null) reach = reach(reach, start.getX(), start.getZ());
		if (join != null) reach = reach(reach, join.at().getX(), join.at().getZ());
		for (ServerPacket packet : server)
			if (packet instanceof Teleport teleport && !teleport.relatives().contains(Relative.X)
					&& !teleport.relatives().contains(Relative.Z))
				reach = reach(reach, teleport.x(), teleport.z());
		for (Blocks box : blocks)
			reach = Math.max(reach, Math.max(Math.max(Math.abs(box.from()[0]), Math.abs(box.to()[0])),
					Math.max(Math.abs(box.from()[2]), Math.abs(box.to()[2]))));
		return (reach >> 4) + 2;
	}

	private static int reach(int reach, double x, double z) {
		return Math.max(reach, Math.max(Math.abs((int) Math.floor(x)), Math.abs((int) Math.floor(z))));
	}

	private static Blocks blocks(JsonNode box) {
		String text = box.path("state").asText();
		Matcher matcher = STATE.matcher(text);
		if (!matcher.matches()) throw new IllegalArgumentException("'" + text + "' is no block state");

		Map<String, String> properties = new LinkedHashMap<>();
		if (matcher.group(2) != null)
			for (String property : matcher.group(2).split(",")) {
				String[] parts = property.split("=", 2);
				properties.put(parts[0], parts[1]);
			}
		return new Blocks(corner(box.path("from")), corner(box.path("to")), matcher.group(1), Map.copyOf(properties));
	}

	private static int[] corner(JsonNode corner) {
		return new int[] {corner.get(0).asInt(), corner.get(1).asInt(), corner.get(2).asInt()};
	}

	private static Keys keys(JsonNode entry) {
		List<String> held = new ArrayList<>();
		for (JsonNode key : entry.path("held")) {
			if (!KEYS.contains(key.asText())) throw new IllegalArgumentException("'" + key.asText() + "' is no key");
			held.add(key.asText());
		}
		return new Keys(entry.path("tick").asInt(), MovementInput.builder()
				.forward(held.contains("forward"))
				.backward(held.contains("backward"))
				.left(held.contains("left"))
				.right(held.contains("right"))
				.jump(held.contains("jump"))
				.sneak(held.contains("sneak"))
				.sprint(held.contains("sprint"))
				.build());
	}

	/**
	 * A box of one block state, both corners included.
	 *
	 * @param from one corner
	 * @param to the opposite corner
	 * @param block the block's namespaced ID
	 * @param properties the properties the state names; the others keep the block's defaults
	 */
	record Blocks(
			int @NotNull [] from,
			int @NotNull [] to,
			@NotNull String block,
			@NotNull Map<String, String> properties
	) {
	}

	/**
	 * The keys held from a tick on.
	 *
	 * @param tick the first tick, 0-based
	 * @param input the held keys
	 */
	record Keys(int tick, @NotNull MovementInput input) {
	}

	/**
	 * A join as over the network: the login, the first position packet, teleport ID {@code 1}, to the join's position
	 * and view, and the course's chunks after some client ticks.
	 *
	 * @param at where the first position packet places the player
	 * @param chunksAfterTicks the client ticks that run before the server sends the course's chunks
	 */
	record Join(@NotNull Position at, int chunksAfterTicks) {
	}
}
