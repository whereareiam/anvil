package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ProcessGroup;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunningScenarioTest {
	@Test
	void playerCleanupFailureMarksProcessesUnsuccessfulAndPreservesBothFailures() {
		List<String> events = new ArrayList<>();
		AssertionError playersFailure = new AssertionError("Player cleanup failed");
		IllegalStateException processesFailure = new IllegalStateException("Process cleanup failed");
		PlayerManager players = (PlayerManager) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PlayerManager.class}, (proxy, method, arguments) -> {
					if (!method.getName().equals("close")) throw new AssertionError(method.getName());
					events.add("players");
					throw playersFailure;
				});
		ProcessGroup processes = (ProcessGroup) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ProcessGroup.class}, (proxy, method, arguments) -> {
					if (!method.getName().equals("finish")) throw new AssertionError(method.getName());
					events.add("processes:" + arguments[0]);
					throw processesFailure;
				});
		var scenario = new RunningScenario(AnvilScenario.builder().name("test").entrypoint("server").build(),
				processes, players);

		assertSame(playersFailure, assertThrows(AssertionError.class, scenario::close));
		scenario.close();

		assertEquals(List.of("players", "processes:false"), events);
		assertArrayEquals(new Throwable[]{processesFailure}, playersFailure.getSuppressed());
	}
}
