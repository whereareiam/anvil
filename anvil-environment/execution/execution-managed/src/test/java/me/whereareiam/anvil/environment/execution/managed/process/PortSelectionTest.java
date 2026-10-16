package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortSelectionTest {
	/** Three neighbouring ports nothing listens on over TCP or UDP, so the small range of a test is entirely free. */
	private static final int FIRST = freeRange();

	@Test
	void doesNotReusePortsBeforeThePlannedListenersBind() {
		PortSelection ports = new PortSelection();
		HashSet<Integer> reserved = new HashSet<>();
		for (int index = 0; index < 500; index++)
			assertTrue(reserved.add(ports.reserve("127.0.0.1", ListenerTransport.TCP)), "Every planned listener needs a distinct port");
	}

	@Test
	void reservesBindableLoopbackPort() throws Exception {
		int port = new PortSelection().reserve("127.0.0.1", ListenerTransport.TCP);
		try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
			assertTrue(socket.isBound());
		}
	}

	@Test
	void reservesOutsideTheEphemeralRangeTheOperatingSystemAssignsFrom() {
		PortSelection ports = new PortSelection();
		for (int index = 0; index < 100; index++)
			assertTrue(ports.reserve("127.0.0.1", ListenerTransport.TCP) < 32768, "An ephemeral port can be taken before the process binds it");
	}

	@Test
	void skipsAPortAnotherListenerHolds() throws Exception {
		PortSelection ports = new PortSelection();
		int taken = ports.reserve("127.0.0.1", ListenerTransport.TCP);
		ports.release(taken);
		try (ServerSocket ignored = new ServerSocket(taken, 1, InetAddress.getByName("127.0.0.1"))) {
			for (int index = 0; index < 2000; index++)
				assertNotEquals(taken, ports.reserve("127.0.0.1", ListenerTransport.TCP));
		}
	}

	@Test
	void handsAReleasedPortOutAgain() {
		PortSelection ports = new PortSelection(FIRST, FIRST + 2);
		int released = ports.reserve("127.0.0.1", ListenerTransport.TCP);
		ports.reserve("127.0.0.1", ListenerTransport.TCP);
		ports.reserve("127.0.0.1", ListenerTransport.TCP);
		assertThrows(ProvisioningException.class, () -> ports.reserve("127.0.0.1", ListenerTransport.TCP));

		ports.release(released);

		assertEquals(released, ports.reserve("127.0.0.1", ListenerTransport.TCP));
	}

	@Test
	void reservesBindableLoopbackDatagramPort() throws Exception {
		int port = new PortSelection().reserve("127.0.0.1", ListenerTransport.UDP);
		try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", port))) {
			assertTrue(socket.isBound());
		}
	}

	@Test
	void skipsAPortAnotherDatagramSocketHolds() throws Exception {
		PortSelection ports = new PortSelection();
		int taken = ports.reserve("127.0.0.1", ListenerTransport.UDP);
		ports.release(taken);
		try (DatagramSocket ignored = new DatagramSocket(new InetSocketAddress("127.0.0.1", taken))) {
			for (int index = 0; index < 2000; index++)
				assertNotEquals(taken, ports.reserve("127.0.0.1", ListenerTransport.UDP));
		}
	}

	@Test
	void keepsReservedNumbersDistinctAcrossTransports() {
		PortSelection ports = new PortSelection(FIRST, FIRST + 2);
		HashSet<Integer> reserved = new HashSet<>();
		assertTrue(reserved.add(ports.reserve("127.0.0.1", ListenerTransport.UDP)));
		assertTrue(reserved.add(ports.reserve("127.0.0.1", ListenerTransport.TCP)));
		assertTrue(reserved.add(ports.reserve("127.0.0.1", ListenerTransport.UDP)));

		assertThrows(ProvisioningException.class, () -> ports.reserve("127.0.0.1", ListenerTransport.TCP),
				"A number reserved for UDP must not be handed out again for TCP");
	}

	private static int freeRange() {
		for (int first = 21000; first < 32000; first += 3)
			if (free(first) && free(first + 1) && free(first + 2)) return first;

		throw new AssertionError("No three neighbouring free ports");
	}

	private static boolean free(int port) {
		try (ServerSocket stream = new ServerSocket(port, 1, InetAddress.getLoopbackAddress());
			 DatagramSocket datagram = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
			return stream.isBound() && datagram.isBound();
		} catch (IOException occupied) {
			return false;
		}
	}
}
