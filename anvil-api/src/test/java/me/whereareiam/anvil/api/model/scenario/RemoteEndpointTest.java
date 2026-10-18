package me.whereareiam.anvil.api.model.scenario;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoteEndpointTest {
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"play.example.com|play.example.com|25565",
			"play.example.com:25570|play.example.com|25570",
			"play.example.com:|play.example.com|25565",
			"Play.Example.com.|Play.Example.com.|25565",
			"203.0.113.7|203.0.113.7|25565",
			"203.0.113.7:25577|203.0.113.7|25577",
			"[2001:db8::7]|2001:db8::7|25565",
			"[2001:db8::7]:25577|2001:db8::7|25577",
			"2001:db8::7|2001:db8::7|25565",
			"bücher.example|xn--bcher-kva.example|25565"
	})
	void readsTheAddressAsTheVanillaClientDoes(String address, String host, int port) {
		RemoteEndpoint remote = remote(address);

		assertEquals(host, remote.host());
		assertEquals(port, remote.port());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"play.example.com:port|its port 'port' is not a number",
			"play.example.com:+1|its port '+1' is not a number",
			"play.example.com:0|its port 0 is outside 1 to 65535",
			"play.example.com:65536|its port 65536 is outside 1 to 65535",
			":25565|it names no host",
			"[2001:db8::7|its IPv6 literal has no closing bracket",
			"[2001:db8::7]x|only a port may follow its IPv6 literal",
			"[]:25565|its brackets hold no host",
			"play example.com|it contains whitespace"
	})
	void refusesAnAddressTheClientCannotConnectTo(String address, String reason) {
		RemoteEndpoint remote = remote(address);

		var refused = assertThrows(IllegalArgumentException.class, () -> {
			remote.host();
			remote.port();
		});

		assertEquals("Remote 'network' declares the address '" + address + "', which cannot be parsed: " + reason,
				refused.getMessage());
	}

	@Test
	void speaksItsDeclaredVersion() {
		assertEquals(MinecraftVersion.parse("1.21.11"), remote("play.example.com").nativeVersion());

		var missing = assertThrows(IllegalArgumentException.class,
				() -> remote("play.example.com").toBuilder().minecraftVersion(" ").build().nativeVersion());
		assertEquals("Remote 'network' declares no Minecraft version", missing.getMessage());

		var invalid = assertThrows(IllegalArgumentException.class,
				() -> remote("play.example.com").toBuilder().minecraftVersion("1.21-pre1").build().nativeVersion());
		assertEquals("Remote 'network' declares an invalid Minecraft version: Not a Minecraft release version: '1.21-pre1'",
				invalid.getMessage());
	}

	private static RemoteEndpoint remote(String address) {
		return RemoteEndpoint.builder().name("network").address(address).minecraftVersion("1.21.11").build();
	}
}
