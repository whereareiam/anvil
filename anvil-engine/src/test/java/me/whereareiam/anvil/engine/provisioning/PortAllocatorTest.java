package me.whereareiam.anvil.engine.provisioning;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PortAllocatorTest {
	@Test
	void doesNotReusePortsBeforeThePlannedListenersBind() {
		PortAllocator allocator = new PortAllocator();
		HashSet<Integer> ports = new HashSet<>();
		for (int index = 0; index < 500; index++)
			assertTrue(ports.add(allocator.allocate("127.0.0.1")), "Every planned listener needs a distinct port");
	}

	@Test
	void allocatesBindableLoopbackPort() throws Exception {
		int port = new PortAllocator().allocate("127.0.0.1");
		try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
			assertTrue(socket.isBound());
		}
	}
}
