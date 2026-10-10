package me.whereareiam.anvil.integration.junit;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioSelectionTest {
	@Test
	void buildsTheScenarioOfAnEnvironmentAnnotationFromItsMembers() throws ReflectiveOperationException {
		assertEquals("lobby-1.21.11-open", select(Inherits.class, "inherited"));
		assertEquals("lobby-26.1.2-whitelist", select(Inherits.class, "overridden"));
	}

	@Test
	void prefersTheMethodDeclarationAndStillSelectsFixedDefinitions() throws ReflectiveOperationException {
		assertEquals("fixed", select(Inherits.class, "fixed"));
		assertEquals("lobby-1.21.11-open", select(FixedType.class, "environment"));
		assertEquals("fixed", select(FixedType.class, "inherited"));
	}

	@Test
	void refusesMissingAndAmbiguousDeclarations() {
		assertEquals("AnvilExtension requires @AnvilTest or an @AnvilEnvironment annotation",
				assertThrows(ExtensionConfigurationException.class, () -> select(Undeclared.class, "none")).getMessage());
		assertTrue(assertThrows(ExtensionConfigurationException.class, () -> select(Inherits.class, "ambiguous")).getMessage()
				.endsWith("declares several Anvil environments; declare exactly one"));
	}

	@Test
	void letsAFactoryHandObjectsToTheScenarioItBuilds() throws ReflectiveOperationException {
		OwnedResources resources = new OwnedResources();

		AnvilScenario scenario = ScenarioSelection.scenario(Stocked.class.getDeclaredMethod("stocked"), Stocked.class, resources);

		assertEquals("stocked-12", scenario.getName());
		assertEquals(new Stock(12), resources.find(Stock.class));
	}

	private static String select(Class<?> type, String method) throws ReflectiveOperationException {
		return ScenarioSelection.scenario(type.getDeclaredMethod(method), type, new OwnedResources()).getName();
	}

	@Target({ElementType.TYPE, ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@AnvilEnvironment(LobbyScenarios.class)
	@interface Lobby {
		String version() default "1.21.11";

		boolean whitelist() default false;
	}

	public static final class LobbyScenarios implements AnvilScenarioFactory<Lobby> {
		@Override
		public @NotNull AnvilScenario create(@NotNull Lobby lobby) {
			return scenario("lobby-" + lobby.version() + (lobby.whitelist() ? "-whitelist" : "-open"));
		}
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@AnvilEnvironment(WarehouseScenarios.class)
	@interface Warehouse {
		int items();
	}

	public static final class WarehouseScenarios implements AnvilScenarioFactory<Warehouse> {
		@Override
		public @NotNull AnvilScenario create(@NotNull Warehouse warehouse, @NotNull ScenarioResources resources) {
			Stock stock = resources.own(new Stock(warehouse.items()));
			return scenario("stocked-" + stock.items());
		}
	}

	private record Stock(int items) {
	}

	private static final class Stocked {
		@Warehouse(items = 12)
		void stocked() { }
	}

	public static final class Fixed implements AnvilScenarioDefinition {
		@Override
		public @NotNull AnvilScenario define() {
			return scenario("fixed");
		}
	}

	@Lobby
	private static final class Inherits {
		void inherited() { }

		@Lobby(version = "26.1.2", whitelist = true)
		void overridden() { }

		@AnvilTest(Fixed.class)
		void fixed() { }

		@Lobby
		@AnvilTest(Fixed.class)
		void ambiguous() { }
	}

	@AnvilTest(Fixed.class)
	private static final class FixedType {
		@Lobby
		void environment() { }

		void inherited() { }
	}

	private static final class Undeclared {
		void none() { }
	}

	private static AnvilScenario scenario(String name) {
		MinecraftServer server = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1")).build();
		return AnvilScenario.builder().name(name).entrypoint(server.getName()).server(server).build();
	}
}
