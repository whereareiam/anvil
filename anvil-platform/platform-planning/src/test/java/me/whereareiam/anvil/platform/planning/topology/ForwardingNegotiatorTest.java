package me.whereareiam.anvil.platform.planning.topology;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ForwardingNegotiatorTest {
	private final ForwardingNegotiator planner = new ForwardingNegotiator();
	private final Map<String, PlatformProvider> providers = Map.of(
			"dual-server", new TestProvider(MinecraftServer.class, List.of(ForwardingMode.MODERN, ForwardingMode.LEGACY)),
			"legacy-server", new TestProvider(MinecraftServer.class, List.of(ForwardingMode.LEGACY)),
			"dual-proxy", new TestProvider(MinecraftProxy.class, List.of(ForwardingMode.MODERN, ForwardingMode.LEGACY)),
			"legacy-proxy", new TestProvider(MinecraftProxy.class, List.of(ForwardingMode.LEGACY)),
			"modern-proxy", new TestProvider(MinecraftProxy.class, List.of(ForwardingMode.MODERN))
	);

	@Test
	void negotiatesDeterministicGroupsWithoutProvisioningOrCredentials() {
		var scenario = AnvilScenario.builder().name("negotiation").entrypoint("proxy")
				.server(server("server", "dual-server")).server(server("direct", "legacy-server"))
				.proxy(proxy("proxy", "dual-proxy", "server")).build();
		var plan = planner.negotiate(ProcessTopology.from(scenario), providers);
		var forwarding = plan.groups().stream().filter(group -> group.processes().contains("proxy")).findFirst().orElseThrow();
		assertEquals(ForwardingMode.MODERN, forwarding.mode());
		assertEquals(Set.of("proxy", "server"), forwarding.processes());
		assertEquals(ForwardingMode.NONE, plan.groups().stream().filter(group -> group.processes().contains("direct"))
				.findFirst().orElseThrow().mode());
		assertEquals(plan.groups(), planner.negotiate(ProcessTopology.from(scenario), providers).groups());
		assertThrows(UnsupportedOperationException.class, () -> forwarding.processes().clear());
	}

	@Test
	void negotiatesOneModeForOverlappingProxyRoutes() {
		var scenario = AnvilScenario.builder().name("shared").entrypoint("first")
				.server(server("lobby", "dual-server")).server(server("game", "legacy-server"))
				.proxy(proxy("first", "dual-proxy", "lobby"))
				.proxy(proxy("second", "dual-proxy", "lobby").toBuilder().server("game").build()).build();
		var plan = planner.negotiate(ProcessTopology.from(scenario), providers);
		assertEquals(1, plan.groups().size());
		assertEquals(4, plan.groups().getFirst().processes().size());
		assertTrue(plan.groups().stream().allMatch(config -> config.mode() == ForwardingMode.LEGACY));
	}

	@Test
	void rejectsIncompatibleModesBeforeProvisioning() {
		var scenario = AnvilScenario.builder().name("incompatible").entrypoint("proxy")
				.server(server("server", "legacy-server"))
				.proxy(proxy("proxy", "modern-proxy", "server")).build();
		assertThrows(ScenarioValidationException.class, () -> planner.negotiate(ProcessTopology.from(scenario), providers));
	}

	@Test
	void rejectsConflictingModernAuthenticationForASharedBackend() {
		var scenario = AnvilScenario.builder().name("authentication").entrypoint("first")
				.server(server("server", "dual-server"))
				.proxy(proxy("first", "modern-proxy", "server"))
				.proxy(proxy("second", "modern-proxy", "server").toBuilder().onlineMode(true).build()).build();
		assertThrows(ScenarioValidationException.class, () -> planner.negotiate(ProcessTopology.from(scenario), providers));
	}

	private MinecraftServer server(String name, String platform) {
		return MinecraftServer.builder().name(name).platform(platform).distribution(Distribution.remote("1.21.11", "1")).build();
	}

	@Test
	void rejectsOnlineAuthenticationOnAForwardedBackend() {
		var scenario = AnvilScenario.builder().name("backend-authentication").entrypoint("proxy")
				.server(server("server", "dual-server").toBuilder().onlineMode(true).build())
				.proxy(proxy("proxy", "dual-proxy", "server").toBuilder().onlineMode(true).build()).build();
		assertThrows(ScenarioValidationException.class, () -> planner.negotiate(ProcessTopology.from(scenario), providers));
	}

	private MinecraftProxy proxy(String name, String platform, String server) {
		return MinecraftProxy.builder().name(name).platform(platform).distribution(Distribution.remote("1", "1"))
				.server(server).defaultServer(server).build();
	}

	@RequiredArgsConstructor
	private static final class TestProvider implements PlatformProvider {
		private final Class<? extends MinecraftProcess> type;
		private final List<ForwardingMode> modes;

		@Override
		public @NotNull String id() {
			return "test";
		}

		@Override
		public @NotNull Class<? extends MinecraftProcess> configurationType() {
			return type;
		}

		@Override
		public @NotNull List<ForwardingMode> forwardingModes() {
			return modes;
		}

		@Override
		public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			throw new AssertionError("Negotiation must not resolve distributions");
		}

		@Override
		public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			throw new AssertionError("Negotiation must not configure processes");
		}

		@Override
		public @NotNull Pattern readinessPattern() {
			return Pattern.compile("ready");
		}

		@Override
		public int minimumJavaVersion(@NotNull MinecraftProcess process) {
			return 21;
		}
	}
}
