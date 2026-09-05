package me.whereareiam.anvil.engine.scenario;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.engine.provisioning.WorkspaceFiles;
import me.whereareiam.anvil.engine.provisioning.WorkspaceSession;
import me.whereareiam.anvil.engine.runtime.process.ManagedProcess;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioResourcesTest {
	@TempDir
	Path temporary;

	@Test
	void continuesAfterAgentAndProcessFailuresAndMarksWorkspaceUnsuccessful() throws Exception {
		List<String> closed = new ArrayList<>();
		RuntimeException agentFailure = new IllegalStateException("agent failed");
		RuntimeException processFailure = new IllegalStateException("proxy failed");
		RuntimeException finalizerFailure = new IllegalStateException("finalizer failed");
		ScenarioResources resources = new ScenarioResources(Duration.ofSeconds(1), successful -> {
			assertFalse(successful);
			closed.add("finalizer");
			throw finalizerFailure;
		});
		resources.addAgent("server", new TestAgent(() -> { throw agentFailure; }));
		resources.addAgent("proxy", new TestAgent(() -> closed.add("agent")));
		resources.addProcess(process("server", () -> closed.add("server")));
		resources.addProcess(process("proxy", () -> {
			closed.add("proxy");
			throw processFailure;
		}));
		Path workspace = temporary.resolve("workspace");
		WorkspacePlan plan = WorkspacePlan.builder().cleanup(WorkspaceCleanup.builder()
				.path(Path.of("failure-marker")).phase(CleanupPhase.ON_FAILURE).build()).build();
		resources.addWorkspace(WorkspaceSession.prepare(temporary, workspace, plan, List.of(), "test",
				temporary.resolve("cache"), new WorkspaceFiles()));
		Files.createFile(workspace.resolve("failure-marker"));

		RuntimeException failure = assertThrows(RuntimeException.class, () -> resources.close(true));

		assertSame(agentFailure, failure);
		assertArrayEquals(new Throwable[] {processFailure, finalizerFailure}, failure.getSuppressed());
		assertEquals(List.of("agent", "proxy", "server", "finalizer"), closed);
		assertFalse(Files.exists(workspace.resolve("failure-marker")));
		resources.close(true);
		assertEquals(4, closed.size());
	}

	@Test
	void closesSuccessfulRunsInReverseProcessOrder() {
		List<String> closed = new ArrayList<>();
		ScenarioResources resources = new ScenarioResources(Duration.ofSeconds(1), successful -> {
			assertTrue(successful);
			closed.add("finalizer");
		});
		resources.addProcess(process("server", () -> closed.add("server")));
		resources.addProcess(process("proxy", () -> closed.add("proxy")));
		resources.close(true);
		assertEquals(List.of("proxy", "server", "finalizer"), closed);
	}

	private ManagedProcess process(String name, Runnable close) {
		return new ManagedProcess(name, new InetSocketAddress("127.0.0.1", 25565), temporary.resolve(name)) {
			@Override
			public void stop(Duration timeout) {
				close.run();
			}
		};
	}

	@RequiredArgsConstructor
	private static final class TestAgent implements AgentClient {
		private final Runnable close;

		@Override
		public <T> T request(@NotNull String operation, @NotNull Object arguments, @NotNull Class<T> responseType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
			return Optional.empty();
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			return false;
		}

		@Override
		public void close() {
			close.run();
		}
	}
}
