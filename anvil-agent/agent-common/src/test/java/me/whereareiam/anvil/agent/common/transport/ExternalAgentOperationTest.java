package me.whereareiam.anvil.agent.common.transport;

import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.common.transport.connection.JsonLineAgentConnectionProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExternalAgentOperationTest {
	private static final AgentOperation<String, String> ECHO = AgentOperation.<String, String>builder()
			.name("external.sample.echo").requestType(String.class).responseType(String.class).build();
	@TempDir
	Path temporary;

	@Test
	void loadsAnExternalJarAndCallsItsTypedOperationThroughTheAuthenticatedClient() throws Exception {
		String provider = "external.sample.EchoOperationProvider";
		String classResource = provider.replace('.', '/') + ".class";
		try (var jar = new JarOutputStream(Files.newOutputStream(temporary.resolve("extension.jar")))) {
			jar.putNextEntry(new JarEntry(classResource));
			try (var input = getClass().getClassLoader().getResourceAsStream(classResource)) {
				assertNotNull(input);
				input.transferTo(jar);
			}
			jar.closeEntry();
			jar.putNextEntry(new JarEntry("META-INF/services/" + AgentOperationProvider.class.getName()));
			jar.write(provider.getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}
		ClassLoader apiLoader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (name.equals(provider))
					throw new ClassNotFoundException(name);
				return super.loadClass(name, resolve);
			}
		};
		try (var extensions = new AgentExtensionLoader(temporary, apiLoader)) {
			var providers = extensions.providers();
			assertEquals(1, providers.size());
			assertNotSame(getClass().getClassLoader(), providers.getFirst().getClass().getClassLoader());
			int port;
			try (var reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
				port = reservation.getLocalPort();
			}
			try (var server = new PlatformAgentServer(port, "secret",
					new PlatformAgentRequestDispatcher(new TestPlatform(), providers), ignored -> { });
				 var client = new JsonLineAgentConnectionProvider().connect(port, "secret", Duration.ofSeconds(2))) {
				assertEquals("echo:hello", client.request(ECHO, "hello"));
				assertTrue(client.identity("missing").isEmpty());
			}
		}
	}

	@Test
	void rejectsDuplicateAndForeignOperationsAndLateRegistration() {
		TestPlatform platform = new TestPlatform();
		assertThrows(IllegalArgumentException.class, () -> new PlatformAgentRequestDispatcher(platform,
				List.of(provider(registry -> {
					registry.register(ECHO, (agent, request) -> request);
					registry.register(ECHO, (agent, request) -> request);
				}))));
		var foreign = AgentOperation.<String, String>builder().name("other.provider.echo")
				.requestType(String.class).responseType(String.class).build();
		assertThrows(IllegalArgumentException.class, () -> new PlatformAgentRequestDispatcher(platform,
				List.of(provider(registry -> registry.register(foreign, (agent, request) -> request)))));
		AtomicReference<AgentOperationRegistry> retained = new AtomicReference<>();
		new PlatformAgentRequestDispatcher(platform, List.of(provider(retained::set)));
		assertThrows(IllegalStateException.class, () -> retained.get().register(ECHO, (agent, request) -> request));
		assertThrows(IllegalArgumentException.class, () -> new PlatformAgentRequestDispatcher(platform,
				List.of(provider(registry -> { }), provider(registry -> { }))));
	}

	private AgentOperationProvider provider(Consumer<AgentOperationRegistry> install) {
		return new AgentOperationProvider() {
			@Override
			public @NotNull String id() {
				return "external.sample";
			}

			@Override
			public void install(@NotNull AgentOperationRegistry registry) {
				install.accept(registry);
			}
		};
	}

	private static final class TestPlatform implements PlatformAgent {
		@Override
		public @NotNull AgentInfo info() {
			return AgentInfo.builder().platform("test").version("1").role(AgentRole.SERVER).build();
		}

		@Override
		public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
			return Optional.empty();
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			return true;
		}
	}
}
