package me.whereareiam.anvil.api.model.service;

import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the services of a scenario are reached from one place: a process, which may reach them at other addresses
 * than the test does, or the test. Anvil fills the placeholders of a process's text assets and JVM arguments with the
 * addresses that process reaches:
 *
 * <ul>
 *   <li>{@code ${anvil.service.<name>.host}}: the host the service is reached at, the same for each of its ports;</li>
 *   <li>{@code ${anvil.service.<name>.port}}: its port, for a service with one port;</li>
 *   <li>{@code ${anvil.service.<name>.port.<container port>}}: the port that reaches one of its container ports.</li>
 * </ul>
 *
 * <pre>{@code
 * redis:
 *   host: "${anvil.service.redis.host}"
 *   port: ${anvil.service.redis.port}
 * }</pre>
 */
public final class ServiceAddresses {
	/**
	 * Addresses without any service.
	 */
	public static final @NotNull ServiceAddresses NONE = new ServiceAddresses(Map.of());

	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{anvil\\.service\\.([a-z0-9-]+)\\.(host|port)(?:\\.(\\d+))?}");

	private final Map<String, Map<Integer, InetSocketAddress>> services;

	private ServiceAddresses(Map<String, Map<Integer, InetSocketAddress>> services) {
		this.services = services;
	}

	/**
	 * Creates the addresses of services, by service name and then by container port.
	 *
	 * @param services where each container port of each service is reached
	 * @return the addresses
	 */
	public static @NotNull ServiceAddresses of(@NotNull Map<String, Map<Integer, InetSocketAddress>> services) {
		Map<String, Map<Integer, InetSocketAddress>> copy = new LinkedHashMap<>();
		services.forEach((name, ports) -> copy.put(name, Collections.unmodifiableMap(new TreeMap<>(ports))));

		return new ServiceAddresses(Collections.unmodifiableMap(copy));
	}

	/**
	 * Returns where one container port of a service is reached.
	 *
	 * @param service       the service's name
	 * @param containerPort the port inside its container
	 * @return the address
	 * @throws IllegalArgumentException when the scenario has no such service or the service no such port
	 */
	public @NotNull InetSocketAddress address(@NotNull String service, int containerPort) {
		InetSocketAddress address = ports(service).get(containerPort);
		if (address == null)
			throw new IllegalArgumentException("Service '" + service + "' does not listen on port " + containerPort);

		return address;
	}

	/**
	 * Fills the service placeholders of a text with these addresses; a text without any is returned as it is.
	 *
	 * @param text the text
	 * @return the text with every placeholder replaced
	 * @throws IllegalArgumentException when a placeholder names a service or port the scenario does not have, or asks
	 *                                  for the port of a service with several without naming one
	 */
	public @NotNull String resolve(@NotNull String text) {
		if (!text.contains("${anvil.service.")) return text;

		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder resolved = new StringBuilder();
		while (matcher.find()) {
			String service = matcher.group(1);
			boolean host = matcher.group(2).equals("host");
			InetSocketAddress address;
			if (matcher.group(3) != null) address = address(service, Integer.parseInt(matcher.group(3)));
			else if (host) address = anyPort(service);
			else address = onlyPort(service, matcher.group());
			String value = host ? address.getHostString() : Integer.toString(address.getPort());
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(resolved);

		return resolved.toString();
	}

	// Every port of a service is reached at the same host.
	private InetSocketAddress anyPort(String service) {
		Map<Integer, InetSocketAddress> ports = ports(service);
		if (ports.isEmpty()) throw new IllegalArgumentException("Service '" + service + "' listens on no port");

		return ports.values().iterator().next();
	}

	private InetSocketAddress onlyPort(String service, String placeholder) {
		Map<Integer, InetSocketAddress> ports = ports(service);
		if (ports.size() != 1)
			throw new IllegalArgumentException(placeholder + " needs a port: service '" + service + "' listens on " + ports.keySet());

		return ports.values().iterator().next();
	}

	private Map<Integer, InetSocketAddress> ports(String service) {
		Map<Integer, InetSocketAddress> ports = services.get(service);
		if (ports == null) throw new IllegalArgumentException("The scenario has no service '" + service + "'");

		return ports;
	}
}
