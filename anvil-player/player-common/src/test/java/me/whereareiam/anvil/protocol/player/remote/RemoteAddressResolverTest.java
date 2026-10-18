package me.whereareiam.anvil.protocol.player.remote;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.scenario.RemoteEndpoint;
import me.whereareiam.anvil.protocol.player.remote.RemoteAddressResolver.Resolution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteAddressResolverTest {
	private static final byte[] PUBLIC = {(byte) 203, 0, 113, 7};
	private static final byte[] SRV_TARGET = {(byte) 198, 51, 100, 25};

	private final StubLookup lookup = new StubLookup();
	private final RemoteAddressResolver resolver = new RemoteAddressResolver(lookup);

	@Test
	void aBareHostFollowsItsSrvRecordAndAnnouncesTheTargetWithItsTrailingDot() throws Exception {
		lookup.records.put("_minecraft._tcp.sylveya.example", "5 25698 25698 play.sylveya.example.");
		lookup.address("play.sylveya.example.", SRV_TARGET);
		lookup.address("sylveya.example", PUBLIC);

		Resolution resolution = resolver.resolve(remote("sylveya.example"));

		assertEquals("198.51.100.25", resolution.address().getHostString());
		assertEquals(25698, resolution.address().getPort());
		assertEquals("play.sylveya.example.", resolution.announcedHost());
		assertEquals(List.of("_minecraft._tcp.sylveya.example"), lookup.queried);
	}

	@Test
	void aHostWithoutAnSrvRecordConnectsToPort25565AndAnnouncesItselfAsTyped() throws Exception {
		lookup.address("Play.Example.test", PUBLIC);

		Resolution resolution = resolver.resolve(remote("Play.Example.test"));

		assertEquals("203.0.113.7", resolution.address().getHostString());
		assertEquals(25565, resolution.address().getPort());
		assertEquals("Play.Example.test", resolution.announcedHost());
	}

	@Test
	void anExplicitPort25565StillLooksTheSrvRecordUp() throws Exception {
		lookup.records.put("_minecraft._tcp.example.test", "0 5 25570 game.example.test.");
		lookup.address("game.example.test.", SRV_TARGET);

		Resolution resolution = resolver.resolve(remote("example.test:25565"));

		assertEquals(25570, resolution.address().getPort());
		assertEquals("game.example.test.", resolution.announcedHost());
	}

	@Test
	void anotherExplicitPortLooksNoSrvRecordUp() throws Exception {
		lookup.records.put("_minecraft._tcp.example.test", "0 5 25570 game.example.test.");
		lookup.address("example.test", PUBLIC);

		Resolution resolution = resolver.resolve(remote("example.test:25580"));

		assertEquals("203.0.113.7", resolution.address().getHostString());
		assertEquals(25580, resolution.address().getPort());
		assertEquals("example.test", resolution.announcedHost());
		assertTrue(lookup.queried.isEmpty());
	}

	@Test
	void anIpLiteralAnnouncesTheNameItsAddressCarries() throws Exception {
		// The system resolver gives a literal no name, so the client's getHostName() asks for a reverse lookup;
		// the stub answers it as the address it returns.
		lookup.addresses.put("203.0.113.7", InetAddress.getByAddress("reverse.example.test", PUBLIC));

		Resolution resolution = resolver.resolve(remote("203.0.113.7:25577"));

		assertEquals("203.0.113.7", resolution.address().getHostString());
		assertEquals(25577, resolution.address().getPort());
		assertEquals("reverse.example.test", resolution.announcedHost());
	}

	@Test
	void anIpv6LiteralInBracketsConnectsToItsAddress() throws Exception {
		byte[] literal = InetAddress.getByName("2001:db8::7").getAddress();
		lookup.addresses.put("2001:db8::7", InetAddress.getByAddress("v6.example.test", literal));

		Resolution resolution = resolver.resolve(remote("[2001:db8::7]:25577"));

		assertEquals(InetAddress.getByAddress(literal), resolution.address().getAddress());
		assertEquals("2001:db8:0:0:0:0:0:7", resolution.address().getHostString());
		assertEquals(25577, resolution.address().getPort());
		assertEquals("v6.example.test", resolution.announcedHost());
	}

	@Test
	void aRecordWithoutFourPartsIsIgnored() throws Exception {
		lookup.records.put("_minecraft._tcp.example.test", "25570 game.example.test.");
		lookup.address("example.test", PUBLIC);

		Resolution resolution = resolver.resolve(remote("example.test"));

		assertEquals(25565, resolution.address().getPort());
		assertEquals("example.test", resolution.announcedHost());
	}

	@Test
	void aRecordPortThatIsNoNumberMeans25565() throws Exception {
		lookup.records.put("_minecraft._tcp.example.test", "0 5 port game.example.test.");
		lookup.address("game.example.test.", SRV_TARGET);

		assertEquals(25565, resolver.resolve(remote("example.test")).address().getPort());
	}

	@Test
	void anInternationalisedHostResolvesInItsAsciiForm() throws Exception {
		lookup.address("xn--bcher-kva.example", PUBLIC);

		Resolution resolution = resolver.resolve(remote("bücher.example"));

		assertEquals("xn--bcher-kva.example", resolution.announcedHost());
		assertEquals(List.of("_minecraft._tcp.xn--bcher-kva.example"), lookup.queried);
	}

	@Test
	void anSrvTargetThatDoesNotResolveDoesNotFallBackToTheTypedHost() throws Exception {
		lookup.records.put("_minecraft._tcp.example.test", "0 5 25570 gone.example.test.");
		lookup.address("example.test", PUBLIC);

		var refused = assertThrows(ScenarioValidationException.class, () -> resolver.resolve(remote("example.test")));

		assertEquals("Remote 'network' at 'example.test' cannot be reached: the host 'gone.example.test.' does not "
				+ "resolve", refused.getMessage());
	}

	private static RemoteEndpoint remote(String address) {
		return RemoteEndpoint.builder().name("network").address(address).minecraftVersion("1.21.11").build();
	}

	/**
	 * Name lookups answering from maps, which records every SRV name it is asked for.
	 */
	private static final class StubLookup implements RemoteAddressResolver.Lookup {
		private final Map<String, String> records = new HashMap<>();
		private final Map<String, InetAddress> addresses = new HashMap<>();
		private final List<String> queried = new ArrayList<>();

		/**
		 * Answers a host with an address carrying that host as its name, as the system resolver does.
		 */
		private void address(String host, byte[] address) throws UnknownHostException {
			addresses.put(host, InetAddress.getByAddress(host, address));
		}

		@Override
		public @Nullable String serviceRecord(@NotNull String name) {
			queried.add(name);
			return records.get(name);
		}

		@Override
		public @NotNull InetAddress address(@NotNull String host) throws UnknownHostException {
			InetAddress address = addresses.get(host);
			if (address == null) throw new UnknownHostException(host);

			return address;
		}
	}
}
