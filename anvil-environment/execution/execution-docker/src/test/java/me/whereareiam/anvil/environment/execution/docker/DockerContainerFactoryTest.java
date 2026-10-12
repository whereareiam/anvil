package me.whereareiam.anvil.environment.execution.docker;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
import me.whereareiam.anvil.environment.execution.docker.model.PortBindings;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DockerContainerFactoryTest {
	private static final InetSocketAddress GAME = new InetSocketAddress("127.0.0.1", 21000);
	private static final InetSocketAddress AGENT = new InetSocketAddress("127.0.0.1", 21001);

	@Test
	void exposesATcpGamePortAndTheAgentPortOverTcp() {
		var exposure = DockerContainerFactory.exposure(PortBindings.builder()
				.game(GAME)
				.agent(AGENT)
				.gameTransport(ListenerTransport.TCP)
				.build());

		assertEquals(List.of(ExposedPort.tcp(DockerContainerFactory.GAME_PORT), ExposedPort.tcp(DockerContainerFactory.AGENT_PORT)),
				exposure.ports());
		assertPublished(exposure.bindings(), ExposedPort.tcp(DockerContainerFactory.GAME_PORT), GAME);
		assertPublished(exposure.bindings(), ExposedPort.tcp(DockerContainerFactory.AGENT_PORT), AGENT);
	}

	@Test
	void exposesAUdpGamePortWhileTheAgentPortStaysTcp() {
		var exposure = DockerContainerFactory.exposure(PortBindings.builder()
				.game(GAME)
				.agent(AGENT)
				.gameTransport(ListenerTransport.UDP)
				.build());

		assertEquals(List.of(ExposedPort.udp(DockerContainerFactory.GAME_PORT), ExposedPort.tcp(DockerContainerFactory.AGENT_PORT)),
				exposure.ports());
		assertPublished(exposure.bindings(), ExposedPort.udp(DockerContainerFactory.GAME_PORT), GAME);
		assertNull(exposure.bindings().getBindings().get(ExposedPort.tcp(DockerContainerFactory.GAME_PORT)),
				"A UDP game listener must not be published over TCP");
	}

	@Test
	void leavesAnUnpublishedGamePortUnexposed() {
		var exposure = DockerContainerFactory.exposure(PortBindings.builder()
				.agent(AGENT)
				.gameTransport(ListenerTransport.UDP)
				.build());

		assertEquals(List.of(ExposedPort.tcp(DockerContainerFactory.AGENT_PORT)), exposure.ports());
	}

	private static void assertPublished(Ports bindings, ExposedPort port, InetSocketAddress host) {
		Ports.Binding[] published = bindings.getBindings().get(port);
		assertEquals(1, published.length);
		assertEquals(host.getHostString(), published[0].getHostIp());
		assertEquals(String.valueOf(host.getPort()), published[0].getHostPortSpec());
	}
}
