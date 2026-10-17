package me.whereareiam.anvil.protocol.mcprotocol.client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Reports a login to a session server other than Mojang's, the step a client performs before an online-mode
 * server verifies it. Client segments call it from the session service they hand to MCProtocolLib, whose own
 * service only addresses Mojang in newer releases.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SessionServerJoin {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Posts the join request to the session server's {@code join} endpoint.
	 *
	 * @param sessionServer base address of the session server
	 * @param accessToken token the session server accepts for the profile
	 * @param profile profile identity that joins
	 * @param serverId server hash derived from the encryption handshake
	 * @throws IOException when the session server cannot be reached or refuses the join
	 */
	public static void report(
			@NotNull URI sessionServer,
			@NotNull String accessToken,
			@NotNull UUID profile,
			@NotNull String serverId
	) throws IOException {
		String body = "{\"accessToken\":\"" + escape(accessToken) + "\",\"selectedProfile\":\""
				+ profile.toString().replace("-", "") + "\",\"serverId\":\"" + escape(serverId) + "\"}";
		HttpRequest request = HttpRequest.newBuilder(URI.create(sessionServer + "/join"))
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2)
				throw new IOException("Session server " + sessionServer + " refused the join with status "
						+ response.statusCode());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while joining through session server " + sessionServer, interrupted);
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
