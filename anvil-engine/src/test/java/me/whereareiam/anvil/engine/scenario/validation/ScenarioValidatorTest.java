package me.whereareiam.anvil.engine.scenario.validation;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.engine.scenario.ScenarioValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.UnknownHostException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioValidatorTest {
	private final ScenarioValidator validator = new ScenarioValidator();

	@ParameterizedTest
	@MethodSource("invalidDeclarations")
	void rejectsInvalidDeclarationsWithUsefulDiagnostics(AnvilScenario scenario, String message) {
		var failure = assertThrows(ScenarioValidationException.class, () -> validator.validate(scenario, true));
		assertEquals(message, failure.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"server", "proxy"})
	void acceptsServerAndProxyEntrypoints(String entrypoint) {
		var scenario = scenario().toBuilder().proxy(proxy()).entrypoint(entrypoint).build();
		assertDoesNotThrow(() -> validator.validate(scenario, true));
	}

	@Test
	void permitsSeveralProxiesToRegisterTheSameServer() {
		var scenario = scenario().toBuilder().proxy(proxy())
				.proxy(proxy().toBuilder().name("second-proxy").build()).build();
		assertDoesNotThrow(() -> validator.validate(scenario, true));
	}

	@Test
	void requiresEulaAcceptanceBeforeCheckingProcessReferences() {
		var scenario = scenario().toBuilder().entrypoint("missing").build();
		var failure = assertThrows(ScenarioValidationException.class, () -> validator.validate(scenario, false));
		assertEquals("Mojang EULA acceptance is required before starting servers", failure.getMessage());
	}

	@ParameterizedTest
	@CsvSource({"false, false", "true, false", "false, true"})
	void requiresBothManualModeAndLanOptInForNonLoopbackBinding(boolean manual, boolean allowLanBinding) {
		var scenario = scenario().toBuilder().bindAddress("0.0.0.0")
				.manual(manual).allowLanBinding(allowLanBinding).build();
		var failure = assertThrows(ScenarioValidationException.class, () -> validator.validate(scenario, true));
		assertEquals("Non-loopback binding requires a manual scenario and allowLanBinding=true", failure.getMessage());
	}

	@Test
	void permitsNonLoopbackBindingForManualScenariosWithLanOptIn() {
		var scenario = scenario().toBuilder().bindAddress("0.0.0.0").manual(true).allowLanBinding(true).build();
		assertDoesNotThrow(() -> validator.validate(scenario, true));
	}

	@ParameterizedTest
	@ValueSource(strings = {"127.0.0.1", "::1"})
	void permitsLoopbackBindingWithoutLanOptIn(String address) {
		var scenario = scenario().toBuilder().bindAddress(address).build();
		assertDoesNotThrow(() -> validator.validate(scenario, true));
	}

	@Test
	void preservesInvalidBindAddressCauseBeforeCheckingEula() {
		var scenario = scenario().toBuilder().bindAddress(":::").build();
		var failure = assertThrows(ScenarioValidationException.class, () -> validator.validate(scenario, false));
		assertEquals("Invalid scenario bind address: :::", failure.getMessage());
		assertInstanceOf(UnknownHostException.class, failure.getCause());
	}

	private static Stream<Arguments> invalidDeclarations() {
		return Stream.of(
				Arguments.of(scenario().toBuilder().name(" ").build(), "Scenario name must not be blank"),
				Arguments.of(scenario().toBuilder().clearServers().build(), "Scenario 'test' has no servers or proxies"),
				Arguments.of(scenario().toBuilder().entrypoint(" ").build(), "Scenario 'test' must declare an entrypoint"),
				Arguments.of(scenario().toBuilder().entrypoint("missing").build(),
						"Scenario entrypoint 'missing' does not reference a server or proxy"),
				Arguments.of(scenario().toBuilder().clearServers().server(server().toBuilder().name(" ").build()).build(),
						"Every server and proxy needs a non-blank name"),
				Arguments.of(scenario().toBuilder().proxy(proxy().toBuilder().name(" ").build()).build(),
						"Every server and proxy needs a non-blank name"),
				Arguments.of(scenario().toBuilder().server(server()).build(), "Duplicate server or proxy name: server"),
				Arguments.of(scenario().toBuilder().proxy(proxy()).proxy(proxy()).build(), "Duplicate server or proxy name: proxy"),
				Arguments.of(scenario().toBuilder().proxy(proxy().toBuilder().name("server").build()).build(),
						"Duplicate server or proxy name: server"),
				Arguments.of(scenario().toBuilder().proxy(proxy().toBuilder().clearServers().build()).build(),
						"Proxy 'proxy' has no registered servers"),
				Arguments.of(scenario().toBuilder().proxy(proxy().toBuilder().server("server").build()).build(),
						"Proxy 'proxy' registers server 'server' more than once"),
				Arguments.of(scenario().toBuilder().proxy(proxy().toBuilder().server("missing").build()).build(),
						"Proxy 'proxy' references missing server 'missing'"),
				Arguments.of(scenario().toBuilder().proxy(proxy().toBuilder().server("proxy").build()).build(),
						"Proxy 'proxy' references missing server 'proxy'"),
				Arguments.of(scenario().toBuilder().proxy(proxy().toBuilder().defaultServer(" ").build()).build(),
						"Proxy 'proxy' must declare a default server"),
				Arguments.of(scenario().toBuilder().server(server().toBuilder().name("other-server").build())
						.proxy(proxy().toBuilder().defaultServer("other-server").build()).build(),
						"Proxy 'proxy' default server 'other-server' is not registered with that proxy")
		);
	}

	private static AnvilScenario scenario() {
		return AnvilScenario.builder().name("test").entrypoint("server").server(server()).build();
	}

	private static MinecraftServer server() {
		return MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1", "1")).build();
	}

	private static MinecraftProxy proxy() {
		return MinecraftProxy.builder().name("proxy").platform("test")
				.distribution(Distribution.remote("1", "1")).server("server").defaultServer("server").build();
	}
}
