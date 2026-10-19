package me.whereareiam.anvil.testkit.tests.server.physics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.scenario.PhysicsScenarioFactory;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The course a judge's Paper server builds through its console, high above the world's own terrain so that every
 * release builds it the same: a stone floor at {@code y = 150}, whose top a standing player's feet rest on at
 * {@code y = 151}, and one lane per journey along the positive x axis, each with a start a player is teleported to,
 * facing along the lane. The server's console also tells where it sees a player and how much health the player has
 * left, and reports every position it forces on the player.
 *
 * <pre>{@code
 * PhysicsCourse course = new PhysicsCourse(anvil, PhysicsEnvironment.Judge.VANILLA);
 * course.build();
 * SimulatedPlayer player = course.join(ignored -> { });
 * course.place(player, PhysicsCourse.Lane.HOVER);
 * PhysicsCourse.Verdict verdict = course.verdict(player);
 * verdict.assertAccepted();
 * }</pre>
 */
public final class PhysicsCourse {
	/**
	 * How long a test waits for the server or a player.
	 */
	public static final Duration TIMEOUT = Duration.ofSeconds(20);
	/**
	 * Height of the top of the course's floor, where a standing player's feet rest.
	 */
	public static final double FLOOR = 151.0D;

	private static final AtomicInteger PLAYERS = new AtomicInteger();
	private static final String READY = "anvil:course-ready";
	private static final String PLACED = "anvil:placed:";
	private static final String NOT_LOADED = "That position is not loaded";
	private static final List<String> FAILURES = List.of(NOT_LOADED, "<--[HERE]", "Unknown or incomplete command",
			"Incorrect argument", "Unknown block type");
	private static final String ENTITY_DATA = " has the following entity data: ";
	private static final Duration LOADING = Duration.ofMillis(500);
	private static final int LOADING_ATTEMPTS = 40;
	// Game rules took registry names in 1.21.11.
	private static final String REGISTRY_GAME_RULES = "1.21.11";

	private final @NotNull ScenarioContext anvil;
	private final @NotNull PhysicsEnvironment.Judge judge;

	/**
	 * Creates the course of a judge's server.
	 *
	 * @param anvil the test's scenario
	 * @param judge the judge whose server holds the course: its Paper server, or the backend behind Sonar
	 */
	public PhysicsCourse(@NotNull ScenarioContext anvil, @NotNull PhysicsEnvironment.Judge judge) {
		this.anvil = anvil;
		this.judge = judge;
	}

	/**
	 * Builds the course anew, replacing whatever an earlier test left there, and waits until the server built it. The
	 * server keeps the course's chunks loaded and stops the time, the weather, natural regeneration and mob spawning,
	 * so that health only changes by damage and nothing else enters the course.
	 *
	 * @throws IllegalStateException when the server refuses one of the commands
	 */
	public void build() {
		console().sendCommand("forceload add 0 0 47 39");
		for (int attempt = 1; ; attempt++) {
			String outcome = runBuild();
			if (outcome.contains(READY)) return;
			if (!outcome.contains(NOT_LOADED) || attempt == LOADING_ATTEMPTS)
				throw new IllegalStateException("The server could not build the physics course: " + outcome);

			pause(LOADING);
		}
	}

	/**
	 * Creates a player with a name no earlier test of this run used, lets the test configure it before it connects,
	 * connects it and waits for the fixture's welcome. With Grim as the judge, Grim prints what it predicted, what the
	 * player did and the offset of every movement of the player to the console from then on.
	 *
	 * @param configure what to set on the player before it connects
	 * @return the connected player
	 */
	public @NotNull SimulatedPlayer join(@NotNull Consumer<SimulatedPlayer> configure) {
		SimulatedPlayer player = anvil.players().create("Physics" + PLAYERS.incrementAndGet());
		configure.accept(player);
		player.capability(Session.class).connect();
		player.capability(Session.class).connected(TIMEOUT);
		player.capability(Messages.class).received("anvil:welcome", TIMEOUT);
		if (judge == PhysicsEnvironment.Judge.GRIM) console().sendCommand("grim consoledebug " + player.name());

		return player;
	}

	/**
	 * Teleports a player to the start of a lane, facing along it, and waits until the server did. The fixture plugin
	 * teleports the player at the start of a tick: the console's own {@code tp} runs after the server ticked its
	 * players' connections, and before Minecraft 1.21.2 the server would then check a vanilla client's confirmation of
	 * the teleport against the position before it and find that the client moved too quickly.
	 *
	 * @param player the player
	 * @param lane the lane
	 */
	public void place(@NotNull SimulatedPlayer player, @NotNull Lane lane) {
		ConsoleWatch output = console().watch();
		console().sendCommand(String.format(Locale.ROOT, "anvil-place %s %.4f %.4f %.4f %.1f %.1f", player.name(),
				lane.getX(), lane.getY(), lane.getZ(), lane.getYaw(), 0.0F));
		output.printed(PLACED + player.name(), TIMEOUT);
	}

	/**
	 * Starts collecting what the judge says about a player's movement from now on.
	 *
	 * @param player the player
	 * @return the verdict, which reads the judge's console from now on
	 */
	public @NotNull Verdict verdict(@NotNull SimulatedPlayer player) {
		return new Verdict(player, console().watch());
	}

	/**
	 * Returns where the server sees a player, as the player's entity data says: its position, view direction and
	 * whether it stands on the ground.
	 *
	 * @param player the player
	 * @return the player's position on the server
	 */
	public @NotNull Position position(@NotNull SimulatedPlayer player) {
		double[] position = numbers(query(player, "Pos"));
		double[] rotation = numbers(query(player, "Rotation"));
		return Position.builder()
				.x(position[0])
				.y(position[1])
				.z(position[2])
				.yaw((float) rotation[0])
				.pitch((float) rotation[1])
				.onGround(query(player, "OnGround").startsWith("1"))
				.build();
	}

	/**
	 * Returns the health the server says a player has.
	 *
	 * @param player the player
	 * @return the player's health, at most 20
	 */
	public float health(@NotNull SimulatedPlayer player) {
		return (float) numbers(query(player, "Health"))[0];
	}

	private String runBuild() {
		ConsoleWatch output = console().watch();
		for (String command : commands())
			console().sendCommand(command);
		console().sendCommand("say " + READY);

		return output.printed(line -> line.contains(READY) || FAILURES.stream().anyMatch(line::contains), TIMEOUT);
	}

	private List<String> commands() {
		boolean registryRules = PhysicsRelease.isAtLeast(REGISTRY_GAME_RULES);
		List<String> commands = new ArrayList<>(List.of(
				"gamerule " + (registryRules ? "natural_health_regeneration" : "naturalRegeneration") + " false",
				"gamerule " + (registryRules ? "spawn_mobs" : "doMobSpawning") + " false",
				"gamerule " + (registryRules ? "advance_time" : "doDaylightCycle") + " false",
				"gamerule " + (registryRules ? "advance_weather" : "doWeatherCycle") + " false",
				"time set day",
				"weather clear",
				// Clears the air above the floor in four parts, each within the fill command's limit.
				"fill 0 140 0 47 190 9 air",
				"fill 0 140 10 47 190 19 air",
				"fill 0 140 20 47 190 29 air",
				"fill 0 140 30 47 190 39 air",
				"fill 0 150 0 47 150 39 stone"
		));
		for (Lane lane : Lane.values())
			commands.addAll(lane.getBlocks());
		commands.add("kill @e[type=!player,x=0,y=140,z=0,dx=48,dy=51,dz=40]");

		return commands;
	}

	private String query(SimulatedPlayer player, String path) {
		ConsoleWatch output = console().watch();
		console().sendCommand("data get entity " + player.name() + " " + path);
		String line = output.printed(player.name() + ENTITY_DATA, TIMEOUT);

		return line.substring(line.indexOf(ENTITY_DATA) + ENTITY_DATA.length()).trim();
	}

	private static double[] numbers(String value) {
		String[] parts = value.replace("[", "").replace("]", "").split(",");
		double[] numbers = new double[parts.length];
		for (int index = 0; index < parts.length; index++)
			numbers[index] = Double.parseDouble(parts[index].trim().replaceAll("[a-zA-Z]+$", ""));

		return numbers;
	}

	private ProcessConsole console() {
		String server = judge == PhysicsEnvironment.Judge.SONAR ? PhysicsScenarioFactory.BACKEND : PhysicsScenarioFactory.SERVER;
		return anvil.processes().server(server).console();
	}

	private static void pause(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while the server loads the physics course", interrupted);
		}
	}

	/**
	 * The course's lanes: where a journey starts and the blocks the server places for it. Every lane runs along the
	 * positive x axis, which a player faces at a yaw of {@code -90}.
	 */
	@Getter
	@RequiredArgsConstructor
	public enum Lane {
		/**
		 * A pillar whose top is 3 blocks above the floor: falling off it does no damage.
		 */
		FALL_3(4.5D, FLOOR + 3, 1.5D, List.of("fill 4 151 1 4 153 1 stone")),
		/**
		 * A pillar whose top is 10 blocks above the floor: falling off it does 7 damage.
		 */
		FALL_10(12.5D, FLOOR + 10, 1.5D, List.of("fill 12 151 1 12 160 1 stone")),
		/**
		 * A pillar whose top is 24 blocks above the floor: falling off it does 21 damage, which kills.
		 */
		FALL_24(20.5D, FLOOR + 24, 1.5D, List.of("fill 20 151 1 20 174 1 stone")),
		/**
		 * Stone, then 8 blocks each of ice, slime and honey set into the floor, then stone again from {@code x = 32}.
		 */
		SURFACES(2.5D, FLOOR, 5.5D, List.of(
				"fill 8 150 5 15 150 5 ice",
				"fill 16 150 5 23 150 5 slime_block",
				"fill 24 150 5 31 150 5 honey_block"
		)),
		/**
		 * A bottom slab at {@code x = 8}, then stairs at {@code x = 12} up onto a block-high strip from {@code x = 13} to
		 * {@code x = 16}.
		 */
		STEPS(2.5D, FLOOR, 9.5D, List.of(
				"fill 8 151 9 8 151 9 stone_slab[type=bottom]",
				"fill 12 151 9 12 151 9 stone_stairs[facing=east,half=bottom]",
				"fill 13 151 9 16 151 9 stone"
		)),
		/**
		 * A lone oak fence post at {@code x = 8}, whose collision box starts at {@code x = 8.375}.
		 */
		FENCE(4.5D, FLOOR, 13.5D, List.of("fill 8 151 13 8 151 13 oak_fence")),
		/**
		 * A lone cobblestone wall post at {@code x = 8}, whose collision box starts at {@code x = 8.25}.
		 */
		WALL(4.5D, FLOOR, 17.5D, List.of("fill 8 151 17 8 151 17 cobblestone_wall")),
		/**
		 * A block-high strip from {@code x = 8} to {@code x = 11} to jump onto.
		 */
		JUMP(4.5D, FLOOR, 21.5D, List.of("fill 8 151 21 11 151 21 stone")),
		/**
		 * Open floor up to {@code x = 47} to sprint-jump along.
		 */
		SPRINT(2.5D, FLOOR, 25.5D, List.of()),
		/**
		 * A block-high strip whose end at {@code x = 8} a sneaking player must not walk off.
		 */
		EDGE(2.5D, FLOOR + 1, 29.5D, List.of("fill 0 151 29 7 151 29 stone")),
		/**
		 * A pool of water three blocks deep, from {@code x = 7} to {@code x = 9}, walled with glass; its surface is at
		 * {@code y = 154}.
		 */
		WATER(8.5D, FLOOR, 33.5D, List.of(
				"fill 6 151 31 10 153 35 glass",
				"fill 7 151 32 9 153 34 water"
		)),
		/**
		 * A stone column six blocks high at {@code x = 8} with ladders on its west face; its top is at {@code y = 157}.
		 */
		LADDER(3.5D, FLOOR, 38.5D, List.of(
				"fill 8 151 38 8 156 38 stone",
				"fill 7 151 38 7 156 38 ladder[facing=west]"
		)),
		/**
		 * Air high above the course, with no block near enough to stand on.
		 */
		HOVER(24.5D, FLOOR + 29, 20.5D, List.of());

		/**
		 * The start's x coordinate.
		 */
		private final double x;
		/**
		 * The start's y coordinate, where the player's feet are.
		 */
		private final double y;
		/**
		 * The start's z coordinate.
		 */
		private final double z;
		/**
		 * The commands that place the lane's blocks on the cleared course.
		 */
		private final @NotNull List<String> blocks;

		/**
		 * Returns the yaw that faces along the lane.
		 *
		 * @return the yaw, facing the positive x axis
		 */
		public float getYaw() {
			return -90.0F;
		}
	}

	/**
	 * What a judge says about one player's movement, from the moment it was created: the positions the server forces
	 * on the player, the server's complaints about the player's moves, and, with Grim, each of Grim's flags.
	 */
	public final class Verdict {
		private final @NotNull SimulatedPlayer player;
		private final @NotNull ConsoleWatch output;
		private final @NotNull Predicate<String> refusal;

		private Verdict(@NotNull SimulatedPlayer player, @NotNull ConsoleWatch output) {
			this.player = player;
			this.output = output;
			String name = player.name();
			Predicate<String> server = line -> line.contains("anvil:correction:" + name + ":")
					|| line.contains(name + " moved wrongly")
					|| line.contains(name + " moved too quickly");
			this.refusal = judge == PhysicsEnvironment.Judge.GRIM ? server.or(this::grimFlag) : server;
		}

		/**
		 * Expects that the judge refused none of the player's movement so far, and none for one more second, in which
		 * Grim prints the flags of the latest moves.
		 *
		 * @throws me.whereareiam.anvil.api.exception.ProcessException when the judge refused a movement, naming it
		 */
		public void assertAccepted() {
			output.neverPrinted(refusal, Duration.ofSeconds(1));
		}

		/**
		 * Waits until Grim flags the player's movement.
		 *
		 * @return Grim's line
		 * @throws IllegalStateException when the judge is not Grim
		 * @throws me.whereareiam.anvil.api.exception.ProcessException when Grim flags nothing in time
		 */
		public @NotNull String awaitFlagged() {
			if (judge != PhysicsEnvironment.Judge.GRIM)
				throw new IllegalStateException("Only Grim flags movement; the judge is " + judge);

			return output.printed(this::grimFlag, TIMEOUT);
		}

		/**
		 * Waits until the server reports the player's death by a fall.
		 *
		 * @return the death message
		 * @throws me.whereareiam.anvil.api.exception.ProcessException when the player does not die in time
		 */
		public @NotNull String awaitFatalFall() {
			return output.printed(player.name() + " fell from a high place", TIMEOUT);
		}

		private boolean grimFlag(String line) {
			return line.contains("Grim") && line.contains(player.name()) && line.contains(" failed ");
		}
	}
}
