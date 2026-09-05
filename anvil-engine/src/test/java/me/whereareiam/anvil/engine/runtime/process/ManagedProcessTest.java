package me.whereareiam.anvil.engine.runtime.process;

import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.engine.AnvilException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedProcessTest {
	@TempDir
	Path temporary;

	@Test
	void supervisesAndStopsAReadyProcess() {
		ManagedServer process = process("ready");
		process.start(
				List.of("sh", "-c", "echo READY; while read line; do [ \"$line\" = stop ] && exit 0; done"),
				Map.of(),
				Pattern.compile("READY"),
				"stop",
				Duration.ofSeconds(3)
		);

		assertEquals(ProcessState.READY, process.state());
		process.stop(Duration.ofSeconds(3));
		assertEquals(ProcessState.STOPPED, process.state());
		assertTrue(process.logs(10).contains("READY"));
	}

	private ManagedServer process(String name) {
		return new ManagedServer(name, new InetSocketAddress("127.0.0.1", 25565), temporary.resolve(name));
	}

	@Test
	void terminatesTheProcessEvenWhenItsConsoleIsClosed() {
		ManagedServer process = process("closed-console");
		process.start(
				List.of("sh", "-c", "exec 0<&-; echo READY; exec sleep 60"),
				Map.of(), Pattern.compile("READY"), "stop", Duration.ofSeconds(3)
		);
		try {
			assertThrows(AnvilException.class,
					() -> process.stop(Duration.ofMillis(100)));
			assertEquals(ProcessState.STOPPED, process.state());
		} finally {
			process.stop(Duration.ofMillis(100));
		}
	}
}
