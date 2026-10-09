package me.whereareiam.anvil.service.mojang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local stand-in for the two Mojang services an online-mode login depends on: the profile lookup that tells
 * whether a username belongs to a paid account, and the session server that verifies a login. A test registers
 * the profiles that exist, so online-mode behavior is tested without a real account and without Mojang.
 *
 * <p>The service runs in the calling JVM. Start it before declaring the scenario, because the scenario names
 * its address, and close it when the tests that use it are done:</p>
 *
 * <pre>{@code
 * try (MojangService mojang = MojangService.start()) {
 *     MinecraftProxy proxy = MinecraftProxy.builder()
 *             .name("proxy")
 *             .platform(Platforms.VELOCITY)
 *             .onlineMode(true)
 *             .sessionServer(mojang.sessionServer())
 *             .build();
 *     SessionIdentity alice = mojang.register("Alice");
 *     // start the scenario, then:
 *     // players.create(PlayerOptions.builder().name("Alice")
 *     //         .authentication(AuthenticationMode.ONLINE).sessionIdentity(alice).build());
 * }
 * }</pre>
 *
 * <p>It answers {@code GET /users/profiles/minecraft/<username>}, {@code POST /session/minecraft/join} and
 * {@code GET /session/minecraft/hasJoined} the way Mojang does for the cases a login meets. It signs nothing,
 * serves no skins and must never be reachable from outside the test machine.</p>
 */
public final class MojangService implements AutoCloseable {
	private static final String PROFILES = "/users/profiles/minecraft/";
	private static final String SESSION = "/session/minecraft";

	private final ObjectMapper json = new ObjectMapper();
	private final Map<String, SessionIdentity> profiles = new ConcurrentHashMap<>();
	private final Set<String> joins = ConcurrentHashMap.newKeySet();
	private final HttpServer server;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private volatile boolean available = true;

	private MojangService(InetSocketAddress address) throws IOException {
		server = HttpServer.create(address, 0);
		server.createContext(PROFILES, exchange -> answer(exchange, this::lookup));
		server.createContext(SESSION + "/join", exchange -> answer(exchange, this::join));
		server.createContext(SESSION + "/hasJoined", exchange -> answer(exchange, this::hasJoined));
		server.setExecutor(executor);
		server.start();
	}

	/**
	 * Starts the service on a free loopback port.
	 *
	 * @return running service, owned by the caller
	 */
	public static @NotNull MojangService start() {
		return start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
	}

	/**
	 * Starts the service on a chosen address, for example one that processes in containers can reach.
	 * Bind beyond loopback only on a network you trust: the service accepts every registered profile's token.
	 *
	 * @param address address and port to listen on; port zero selects a free port
	 * @return running service, owned by the caller
	 */
	public static @NotNull MojangService start(@NotNull InetSocketAddress address) {
		try {
			return new MojangService(address);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not start the Mojang service on " + address, exception);
		}
	}

	/**
	 * Returns the session server address to declare on processes and that registered identities carry.
	 *
	 * @return base address of the {@code join} and {@code hasJoined} endpoints
	 */
	public @NotNull URI sessionServer() {
		return URI.create(origin() + SESSION);
	}

	/**
	 * Returns the profile lookup address; append a username to ask whether it is a paid account.
	 *
	 * @return address ending in a slash, answering 200 for a registered username and 404 otherwise
	 */
	public @NotNull URI profileLookup() {
		return URI.create(origin() + PROFILES);
	}

	/**
	 * Registers a paid account with a random identity.
	 *
	 * @param username profile name, matched without regard to letter case
	 * @return identity a player signs in with
	 */
	public @NotNull SessionIdentity register(@NotNull String username) {
		return register(username, UUID.randomUUID());
	}

	/**
	 * Registers a paid account, replacing an account already registered under the username.
	 *
	 * @param username profile name, matched without regard to letter case
	 * @param uniqueId profile identity
	 * @return identity a player signs in with
	 */
	public @NotNull SessionIdentity register(@NotNull String username, @NotNull UUID uniqueId) {
		SessionIdentity identity = SessionIdentity.builder()
				.username(username)
				.uniqueId(uniqueId)
				.accessToken(UUID.randomUUID().toString())
				.sessionServer(sessionServer())
				.build();
		profiles.put(key(username), identity);

		return identity;
	}

	/**
	 * Makes every endpoint fail with a server error, as during an outage, or work again.
	 *
	 * @param available false to simulate the outage
	 */
	public void available(boolean available) {
		this.available = available;
	}

	/**
	 * Forgets every registered account and reported login and ends a simulated outage.
	 */
	public void reset() {
		profiles.clear();
		joins.clear();
		available = true;
	}

	/**
	 * Stops the service. Requests in progress are abandoned.
	 */
	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

	private String origin() {
		InetSocketAddress address = server.getAddress();
		String host = address.getAddress().getHostAddress();

		return "http://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + address.getPort();
	}

	private Response lookup(HttpExchange exchange) {
		String path = exchange.getRequestURI().getPath();
		SessionIdentity profile = profiles.get(key(path.substring(PROFILES.length())));

		return profile == null ? Response.status(404) : Response.profile(json, profile);
	}

	/**
	 * A client reports the login it is about to complete; only the profile's own token may do so.
	 */
	private Response join(HttpExchange exchange) throws IOException {
		JsonNode request = json.readTree(exchange.getRequestBody());
		String selected = request.path("selectedProfile").asText();
		String serverId = request.path("serverId").asText();
		for (SessionIdentity profile : profiles.values()) {
			if (!undashed(profile.getUniqueId()).equals(selected)) continue;
			if (!profile.getAccessToken().equals(request.path("accessToken").asText())) break;

			joins.add(key(profile.getUsername()) + '\n' + serverId);
			return Response.status(204);
		}

		return Response.status(403);
	}

	/**
	 * A server asks whether the named player reported this login; each reported login verifies once.
	 */
	private Response hasJoined(HttpExchange exchange) {
		Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
		String username = query.getOrDefault("username", "");
		SessionIdentity profile = profiles.get(key(username));
		if (profile == null || !joins.remove(key(username) + '\n' + query.getOrDefault("serverId", "")))
			return Response.status(204);

		return Response.profile(json, profile);
	}

	private void answer(HttpExchange exchange, Endpoint endpoint) throws IOException {
		try (exchange) {
			Response response = available ? endpoint.answer(exchange) : Response.status(503);
			if (response.body.length == 0) {
				exchange.sendResponseHeaders(response.status, -1);
				return;
			}

			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(response.status, response.body.length);
			exchange.getResponseBody().write(response.body);
		}
	}

	private static Map<String, String> query(String raw) {
		Map<String, String> parameters = new HashMap<>();
		if (raw == null) return parameters;

		for (String pair : raw.split("&")) {
			int separator = pair.indexOf('=');
			if (separator < 0) continue;
			parameters.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
					URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
		}

		return parameters;
	}

	private static String key(String username) {
		return username.toLowerCase(Locale.ROOT);
	}

	private static String undashed(UUID uniqueId) {
		return uniqueId.toString().replace("-", "");
	}

	private interface Endpoint {
		Response answer(HttpExchange exchange) throws IOException;
	}

	private record Response(int status, byte[] body) {
		private static Response status(int status) {
			return new Response(status, new byte[0]);
		}

		private static Response profile(ObjectMapper json, SessionIdentity profile) {
			ObjectNode body = json.createObjectNode()
					.put("id", undashed(profile.getUniqueId()))
					.put("name", profile.getUsername());
			body.putArray("properties");

			return new Response(200, body.toString().getBytes(StandardCharsets.UTF_8));
		}
	}
}
