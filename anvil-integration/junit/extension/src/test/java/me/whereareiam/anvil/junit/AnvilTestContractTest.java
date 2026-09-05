package me.whereareiam.anvil.junit;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnvilTestContractTest {
	@Test
	void selectsOneScenarioDefinitionByType() throws ReflectiveOperationException {
		Method method = Fixture.class.getDeclaredMethod("scenario");
		AnvilTest annotation = method.getAnnotation(AnvilTest.class);

		assertEquals(ExampleScenario.class, annotation.value());
		assertEquals("typed", annotation.value().getDeclaredConstructor().newInstance().define().getName());
	}

	private static final class Fixture {
		@AnvilTest(ExampleScenario.class)
		void scenario() { }
	}

	public static final class ExampleScenario implements AnvilScenarioDefinition {
		@Override
		public @NotNull AnvilScenario define() {
			MinecraftServer server = MinecraftServer.builder()
					.name("server")
					.platform("test")
					.distribution(Distribution.remote("1.21.11", "1"))
					.build();
			return AnvilScenario.builder()
					.name("typed")
					.entrypoint(server.getName())
					.server(server)
					.build();
		}
	}
}
