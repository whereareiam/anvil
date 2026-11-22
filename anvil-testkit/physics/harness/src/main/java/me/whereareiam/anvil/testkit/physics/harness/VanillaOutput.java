package me.whereareiam.anvil.testkit.physics.harness;

/**
 * Something the harness reads from a running vanilla client and writes, such as the traces of the courses.
 */
public interface VanillaOutput {
	/**
	 * Reads from {@code client}, which has bootstrapped, and writes the output.
	 *
	 * @param client    the bootstrapped client
	 * @param minecraft the Minecraft version the client reports
	 * @throws Exception when the output cannot be read or written
	 */
	void write(VanillaClient client, String minecraft) throws Exception;
}
