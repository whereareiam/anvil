package me.whereareiam.anvil.environment.execution.managed.process;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Stands in for an engine in another JVM: reserves the one port it is given, prints it, and holds the
 * reservation until its standard input closes.
 */
final class PortReservationHolder {
	public static void main(String[] arguments) throws IOException {
		int port = Integer.parseInt(arguments[1]);
		PortSelection ports = new PortSelection(Path.of(arguments[0]), port, port);
		System.out.println(ports.reserve("127.0.0.1"));
		System.out.flush();

		while (System.in.read() >= 0) {
			// Holds the reservation until the test closes the input.
		}
	}
}
