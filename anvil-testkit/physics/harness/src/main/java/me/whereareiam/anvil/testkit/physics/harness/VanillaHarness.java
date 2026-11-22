package me.whereareiam.anvil.testkit.physics.harness;

import me.whereareiam.anvil.testkit.physics.harness.block.BlockOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs one vanilla client headless and writes what it was asked to read from it. Each family's main class hands it
 * the family's {@link VanillaClient}.
 *
 * <p>The arguments are the Minecraft version the client must report, then one or more outputs, each an option with
 * its paths:
 * <pre>{@code
 * <minecraft> --traces <courses directory> <traces directory> --blocks <block data directory>
 * }</pre>
 * The client bootstraps once and every output reads the same running client: the traces of the courses, and the block
 * data read from the client's own registries.
 */
public final class VanillaHarness {
	/**
	 * The seed of every random source the client's movement can reach.
	 */
	public static final long SEED = 0L;

	/**
	 * The ID of the player's own entity, which the server's login names, as Anvil's reference test logs its player in.
	 */
	public static final int ENTITY_ID = 7;

	/**
	 * The name and ID of the player's profile.
	 */
	public static final String PROFILE_NAME = "Trace";

	private VanillaHarness() {
	}

	/**
	 * Bootstraps {@code client} and writes the outputs that {@code arguments} name.
	 *
	 * @param arguments the expected Minecraft version and the outputs
	 * @param client    the family's client
	 * @throws Exception when the arguments are wrong, the client reports another version, or an output fails
	 */
	public static void run(String[] arguments, VanillaClient client) throws Exception {
		if (arguments.length < 1)
			throw new IllegalArgumentException("Expected <minecraft version> followed by outputs such as --traces <courses> <traces>");
		String expected = arguments[0];
		List<VanillaOutput> outputs = outputs(arguments);

		String version = client.bootstrap();
		if (!version.equals(expected))
			throw new IllegalStateException("The client reports Minecraft " + version + ", expected " + expected);
		for (VanillaOutput output : outputs)
			output.write(client, version);
	}

	private static List<VanillaOutput> outputs(String[] arguments) {
		List<VanillaOutput> outputs = new ArrayList<>();
		for (int index = 1; index < arguments.length; ) {
			String option = arguments[index];
			if (option.equals("--traces") && index + 2 < arguments.length) {
				outputs.add(new TraceOutput(Path.of(arguments[index + 1]), Path.of(arguments[index + 2])));
				index += 3;
				continue;
			}
			if (option.equals("--blocks") && index + 1 < arguments.length) {
				outputs.add(new BlockOutput(Path.of(arguments[index + 1])));
				index += 2;
				continue;
			}
			throw new IllegalArgumentException("Unknown output " + option + "; expected --traces <courses> <traces> or --blocks <directory>");
		}
		if (outputs.isEmpty())
			throw new IllegalArgumentException("Name at least one output, such as --traces <courses> <traces>");
		return outputs;
	}
}
