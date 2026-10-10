package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortSelectionTest {
	/** Three neighbouring ports nothing listens on, so the small range of a test is entirely free. */
	private static final int FIRST = freeRange();

	@Test
	void doesNotReusePortsBeforeThePlannedListenersBind() {
		PortSelection ports = new PortSelection();
		HashSet<Integer> reserved = new HashSet<>();
		for (int index = 0; index < 500; index++)
			assertTrue(reserved.add(ports.reserve("127.0.0.1")), "Every planned listener needs a distinct port");
	}

	@Test
	void reservesBindableLoopbackPort() throws Exception {
		int port = new PortSelection().reserve("127.0.0.1");
		try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
			assertTrue(socket.isBound());
		}
	}

	@Test
	void reservesOutsideTheEphemeralRangeTheOperatingSystemAssignsFrom() {
		PortSelection ports = new PortSelection();
		for (int index = 0; index < 100; index++)
			assertTrue(ports.reserve("127.0.0.1") < 32768, "An ephemeral port can be taken before the process binds it");
	}

	@Test
	void skipsAPortAnotherListenerHolds() throws Exception {
		PortSelection ports = new PortSelection();
		int taken = ports.reserve("127.0.0.1");
		ports.release(taken);
		try (ServerSocket ignored = new ServerSocket(taken, 1, InetAddress.getByName("127.0.0.1"))) {
			for (int index = 0; index < 2000; index++)
				assertNotEquals(taken, ports.reserve("127.0.0.1"));
		}
	}

	@Test
	void handsAReleasedPortOutAgain() {
		PortSelection ports = new PortSelection(FIRST, FIRST + 2);
		int released = ports.reserve("127.0.0.1");
		ports.reserve("127.0.0.1");
		ports.reserve("127.0.0.1");
		assertThrows(ProvisioningException.class, () -> ports.reserve("127.0.0.1"));

		ports.release(released);

		assertEquals(released, ports.reserve("127.0.0.1"));
	}

	private static int freeRange() {
		for (int first = 21000; first < 32000; first += 3)
			if (free(first) && free(first + 1) && free(first + 2)) return first;

		throw new AssertionError("No three neighbouring free ports");
	}

	private static boolean free(int port) {
		try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
			return socket.isBound();
		} catch (IOException occupied) {
			return false;
		}
	}
}
