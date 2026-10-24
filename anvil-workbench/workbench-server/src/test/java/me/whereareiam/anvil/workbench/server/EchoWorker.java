package me.whereareiam.anvil.workbench.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A JVM the tests start on the workbench outside managed execution: it echoes one line to standard output and
 * error and exits with 3.
 */
public final class EchoWorker {
	public static void main(String[] arguments) throws Exception {
		String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
		System.out.println("echo:" + line + ":" + String.join(",", arguments));
		System.out.flush();
		System.err.println("error:" + line);
		System.err.flush();
		System.exit(3);
	}
}
