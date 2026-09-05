package me.whereareiam.anvil.engine.process;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PortSelectionTest {
	@Test
	void doesNotReusePortsBeforeThePlannedListenersBind() {
		PortSelection allocator = new PortSelection();
		HashSet<Integer> ports = new HashSet<>();
		for (int index = 0; index < 500; index++)
			assertTrue(ports.add(allocator.select("127.0.0.1")), "Every planned listener needs a distinct port");
	}

	@Test
	void allocatesBindableLoopbackPort() throws Exception {
		int port = new PortSelection().select("127.0.0.1");
		try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
			assertTrue(socket.isBound());
		}
	}
}
