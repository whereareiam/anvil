package me.whereareiam.anvil.service.mojang;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangServiceTest {
	private final MojangService mojang = MojangService.start();
	private final HttpClient client = HttpClient.newHttpClient();

	@AfterEach
	void stop() {
		client.close();
		mojang.close();
	}

	@Test
	void looksUpOnlyRegisteredUsernamesWhateverTheirCase() throws Exception {
		UUID uniqueId = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		mojang.register("Alice", uniqueId);

		HttpResponse<String> found = get(mojang.profileLookup() + "alice");
		assertEquals(200, found.statusCode());
		assertEquals("{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Alice\",\"properties\":[]}", found.body());
		assertEquals(404, get(mojang.profileLookup() + "Bob").statusCode());
	}

	@Test
	void verifiesALoginOnceAfterTheProfileReportedItWithItsOwnToken() throws Exception {
		SessionIdentity alice = mojang.register("Alice");
		String verification = alice.getSessionServer() + "/hasJoined?username=Alice&serverId=hash&ip=127.0.0.1";

		assertEquals(204, get(verification).statusCode());
		assertEquals(403, join(alice, "another-token", "hash").statusCode());
		assertEquals(204, get(verification).statusCode());

		assertEquals(204, join(alice, alice.getAccessToken(), "hash").statusCode());
		assertEquals(204, get(alice.getSessionServer() + "/hasJoined?username=Alice&serverId=other").statusCode());
		HttpResponse<String> verified = get(verification);
		assertEquals(200, verified.statusCode());
		assertTrue(verified.body().contains("\"name\":\"Alice\""), verified.body());
		assertEquals(204, get(verification).statusCode());
	}

	@Test
	void failsEveryEndpointDuringAnOutageUntilReset() throws Exception {
		mojang.register("Alice");
		mojang.available(false);
		assertEquals(503, get(mojang.profileLookup() + "Alice").statusCode());

		mojang.reset();
		assertEquals(404, get(mojang.profileLookup() + "Alice").statusCode());
	}

	private HttpResponse<String> get(String address) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create(address)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> join(SessionIdentity identity, String token, String serverId) throws Exception {
		String body = "{\"accessToken\":\"" + token + "\",\"selectedProfile\":\""
				+ identity.getUniqueId().toString().replace("-", "") + "\",\"serverId\":\"" + serverId + "\"}";
		return client.send(HttpRequest.newBuilder(URI.create(identity.getSessionServer() + "/join"))
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
}
