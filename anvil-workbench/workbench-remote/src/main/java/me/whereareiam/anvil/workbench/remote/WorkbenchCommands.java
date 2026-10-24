package me.whereareiam.anvil.workbench.remote;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.workbench.remote.pairing.Pairing;
import me.whereareiam.anvil.workbench.remote.pairing.Pairings;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;

/**
 * The engine side of pairing, run from the workbench JAR on the engine's machine: {@code add} pairs with a
 * workbench through the code it printed, {@code list} shows the pairings, {@code default} chooses the workbench
 * runs use when nothing names one ({@code local} for none), and {@code remove} forgets a pairing.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkbenchCommands {
	/**
	 * Runs one command.
	 *
	 * @param arguments command and its arguments
	 * @throws IOException when the pairings cannot be read or written
	 */
	public static void main(String[] arguments) throws IOException {
		Pairings pairings = Pairings.standard();
		String command = arguments.length == 0 ? "list" : arguments[0];
		switch (command) {
			case "add" -> add(pairings, arguments);
			case "list" -> list(pairings);
			case "default" -> setDefault(pairings, arguments);
			case "remove" -> remove(pairings, arguments);
			default -> usage();
		}
	}

	private static void add(Pairings pairings, String[] arguments) throws IOException {
		if (arguments.length != 4) usage();

		String engine = InetAddress.getLocalHost().getHostName();
		Pairing pairing = WorkbenchConnection.pair(arguments[1], arguments[2], arguments[3], engine);
		pairings.save(pairing);
		System.out.println("Paired with workbench '" + pairing.getName() + "' at " + pairing.getAddress()
				+ " (certificate " + pairing.getFingerprint() + ")");
		if (pairings.defaultName().isEmpty()) {
			pairings.setDefault(pairing.getName());
			System.out.println("Runs now use it by default; 'default local' runs them on this machine again.");
		}
		System.out.println("Pairings are kept in " + pairings.directory());
	}

	private static void list(Pairings pairings) throws IOException {
		Optional<String> preferred = pairings.defaultName();
		System.out.println((preferred.isEmpty() ? "* " : "  ") + "local (this machine)");
		for (Pairing pairing : pairings.all())
			System.out.println((preferred.filter(pairing.getName()::equals).isPresent() ? "* " : "  ")
					+ pairing.getName() + "  " + pairing.getAddress());
	}

	private static void setDefault(Pairings pairings, String[] arguments) throws IOException {
		if (arguments.length != 2) usage();

		pairings.setDefault(arguments[1].equals("local") ? null : arguments[1]);
		System.out.println("Runs now use " + (arguments[1].equals("local") ? "this machine" : "workbench '" + arguments[1] + "'")
				+ " unless anvil.workbench names another.");
	}

	private static void remove(Pairings pairings, String[] arguments) throws IOException {
		if (arguments.length != 2) usage();

		System.out.println(pairings.remove(arguments[1]) ? "Removed workbench '" + arguments[1] + "'" : "No workbench '" + arguments[1] + "' is paired");
	}

	private static void usage() {
		System.err.println("Usage: add NAME HOST:PORT CODE | list | default NAME|local | remove NAME");
		System.exit(2);
	}
}
