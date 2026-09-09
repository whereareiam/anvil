package me.whereareiam.anvil.testkit.fixtures.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

/**
 * Announces readiness and waits for a stop command, optionally recording its shutdown order.
 */
public final class TestProcess {

	public static void main(String[] args) throws Exception {
		System.out.println("READY");
		try (Scanner input = new Scanner(System.in)) {
			while (input.hasNextLine()) {
				if (!input.nextLine().equals("stop")) continue;
				if (args.length == 2)
					Files.writeString(Path.of(args[0]), args[1] + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				return;
			}
		}
	}
}
