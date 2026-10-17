package me.whereareiam.anvil.agent.client.transport;

import me.whereareiam.anvil.agent.client.api.AgentArtifactLocator;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.agent.client.transport.connection.JsonLineAgentConnectionProvider;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentClientDiscoveryTest {
	@Test
	void providesHostServicesWithoutInstallingAnEmbeddedEndpoint() {
		ClassLoader loader = getClass().getClassLoader();
		assertInstanceOf(JsonLineAgentConnectionProvider.class,
				ServiceLoader.load(AgentConnectionProvider.class, loader).findFirst().orElseThrow());
		assertInstanceOf(ClasspathAgentArtifactLocator.class,
				ServiceLoader.load(AgentArtifactLocator.class, loader).findFirst().orElseThrow());
		assertNull(loader.getResource("META-INF/services/"
				+ "me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider"));
	}
}
