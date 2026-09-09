package me.whereareiam.anvil.agent.server.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.client.transport.connection.JsonLineAgentConnectionProvider;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExternalAgentOperationTest {
	private static final AgentOperation<String, String> ECHO = AgentOperation.<String, String>builder()
			.name("external.sample.echo").requestType(String.class).responseType(String.class).build();
	private static final AgentOperation<Void, String> NO_INPUT = AgentOperation.<Void, String>builder()
			.name("external.sample.no-input").requestType(Void.class).responseType(String.class).build();
	private static final AgentOperation<Void, Void> NO_RESULT = AgentOperation.<Void, Void>builder()
			.name("external.sample.no-result").requestType(Void.class).responseType(Void.class).build();
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
	void acceptsNullForVoidRequestsAndRejectsNullForRequiredSchemasBeforeCallingTheHandler() throws Exception {
		AtomicInteger requiredCalls = new AtomicInteger();
		var mapper = new ObjectMapper();
		var number = AgentOperation.<Integer, Integer>builder().name("external.sample.number")
				.requestType(int.class).responseType(Integer.class).build();
		var dispatcher = new PlatformAgentRequestDispatcher(new TestPlatform(), List.of(provider(registry -> {
			registry.register(NO_INPUT, (platform, request) -> {
				assertNull(request);
				return "without-input";
			});
			registry.register(ECHO, (platform, request) -> {
				requiredCalls.incrementAndGet();
				return request;
			});
			registry.register(number, (platform, request) -> {
				requiredCalls.incrementAndGet();
				return request;
			});
		})));

		assertEquals("without-input", dispatcher.handle(NO_INPUT.getName(), mapper.nullNode()).asText());
		var failure = assertThrows(IllegalArgumentException.class,
				() -> dispatcher.handle(ECHO.getName(), mapper.nullNode()));
		assertTrue(failure.getMessage().contains("Missing request"));
		assertThrows(IllegalArgumentException.class, () -> dispatcher.handle(number.getName(), mapper.nullNode()));
		assertEquals(0, requiredCalls.get());
		assertEquals("present", dispatcher.handle(ECHO.getName(), mapper.valueToTree("present")).asText());
		assertEquals(1, requiredCalls.get());
	}

	@Test
	void roundTripsVoidRequestsAndResponsesAndKeepsTheConnectionAfterRejectingANullRequest() throws Exception {
		AtomicInteger noResultCalls = new AtomicInteger();
		var dispatcher = new PlatformAgentRequestDispatcher(new TestPlatform(), List.of(provider(registry -> {
			registry.register(NO_INPUT, (platform, request) -> {
				assertNull(request);
				return "without-input";
			});
			registry.register(NO_RESULT, (platform, request) -> {
				assertNull(request);
				noResultCalls.incrementAndGet();
				return null;
			});
			registry.register(ECHO, (platform, request) -> request);
		})));
		int port;
		try (var reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = reservation.getLocalPort();
		}
		try (var server = new PlatformAgentServer(port, "void-secret", dispatcher, ignored -> { });
		     var client = new JsonLineAgentConnectionProvider().connect(port, "void-secret", Duration.ofSeconds(2))) {
			assertEquals("without-input", client.request(NO_INPUT, null));
			assertNull(client.request(NO_RESULT, null));
			assertEquals(1, noResultCalls.get());
			var failure = assertThrows(AgentException.class, () -> client.request(ECHO, null));
			assertTrue(failure.getMessage().contains("Missing request"));
			assertEquals("after-rejection", client.request(ECHO, "after-rejection"));
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
