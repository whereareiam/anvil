package me.whereareiam.anvil.api.model.process;

import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the scenario's processes reach each other: each process's game address and the addresses of its
 * {@link MinecraftProcess#getListeners() listeners}, as the execution provider lets its peers reach them. Anvil fills
 * the placeholders of a process's text assets and JVM and program arguments with them:
 *
 * <ul>
 *   <li>{@code ${anvil.process.<name>.host}}: the host the process is reached at;</li>
 *   <li>{@code ${anvil.process.<name>.port}}: its game port, or a container's main port;</li>
 *   <li>{@code ${anvil.process.<name>.port.<port>}}: the port that reaches one of a container's ports;</li>
 *   <li>{@code ${anvil.process.<name>.listener.<listener>}}: the port of one of its listeners, which the process
 *   itself binds.</li>
 * </ul>
 *
 * <pre>{@code
 * MinecraftProxy.builder()
 *         .name("proxy")
 *         .listener("link")
 *         // ...
 *
 * // In a server's text asset:
 * proxy:
 *   host: "${anvil.process.proxy.host}"
 *   port: ${anvil.process.proxy.listener.link}
 * }</pre>
 *
 * Only processes of the same lifetime are named: a process kept for the engine is prepared before the scenario's
 * own, so their listeners cannot name each other.
 */
public final class ProcessAddresses {
	/**
	 * Addresses without any process.
	 */
	public static final @NotNull ProcessAddresses NONE = new ProcessAddresses(Map.of(), Map.of());

	private static final Pattern PLACEHOLDER = Pattern.compile(
			"\\$\\{anvil\\.process\\.([A-Za-z0-9_-]+)\\.(host|port|port\\.(\\d+)|listener\\.([a-z0-9-]+))}");

	private final Map<String, InetSocketAddress> games;
	private final Map<String, Map<String, InetSocketAddress>> listeners;

	private ProcessAddresses(Map<String, InetSocketAddress> games, Map<String, Map<String, InetSocketAddress>> listeners) {
		this.games = games;
		this.listeners = listeners;
	}

	/**
	 * Creates the addresses of processes.
	 *
	 * @param games     where peers reach each process's game port, by process name
	 * @param listeners where peers reach each process's listeners, by process name and then listener name; a
	 *                  container's ports are its listeners, named by their numbers
	 * @return the addresses
	 */
	public static @NotNull ProcessAddresses of(
			@NotNull Map<String, InetSocketAddress> games,
			@NotNull Map<String, Map<String, InetSocketAddress>> listeners
	) {
		Map<String, Map<String, InetSocketAddress>> copy = new LinkedHashMap<>();
		listeners.forEach((process, named) -> copy.put(process, Map.copyOf(named)));

		return new ProcessAddresses(Map.copyOf(games), Collections.unmodifiableMap(copy));
	}

	/**
	 * Returns where peers reach one listener of a process.
	 *
	 * @param process  the process's name
	 * @param listener the listener's name
	 * @return the address
	 * @throws IllegalArgumentException when the scenario has no such process or the process no such listener
	 */
	public @NotNull InetSocketAddress listener(@NotNull String process, @NotNull String listener) {
		InetSocketAddress address = listeners.getOrDefault(process, Map.of()).get(listener);
		if (address == null) {
			game(process);
			throw new IllegalArgumentException("Process '" + process + "' declares no listener '" + listener + "'");
		}

		return address;
	}

	/**
	 * Fills the process placeholders of a text with these addresses; a text without any is returned as it is.
	 *
	 * @param text the text
	 * @return the text with every placeholder replaced
	 * @throws IllegalArgumentException when a placeholder names a process or listener these addresses do not have
	 */
	public @NotNull String resolve(@NotNull String text) {
		if (!text.contains("${anvil.process.")) return text;

		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder resolved = new StringBuilder();
		while (matcher.find()) {
			String process = matcher.group(1);
			String value;
			if (matcher.group(2).equals("host")) value = game(process).getHostString();
			else if (matcher.group(2).equals("port")) value = Integer.toString(game(process).getPort());
			else if (matcher.group(3) != null) value = Integer.toString(listener(process, matcher.group(3)).getPort());
			else value = Integer.toString(listener(process, matcher.group(4)).getPort());
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(resolved);

		return resolved.toString();
	}

	private InetSocketAddress game(String process) {
		InetSocketAddress address = games.get(process);
		if (address == null)
			throw new IllegalArgumentException("No process '" + process + "' of the same lifetime runs in the scenario");

		return address;
	}
}
