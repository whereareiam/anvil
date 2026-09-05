package me.whereareiam.anvil.engine.scenario;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.engine.provisioning.WorkspaceSession;
import me.whereareiam.anvil.engine.provisioning.PortAllocator;
import me.whereareiam.anvil.engine.runtime.process.ManagedProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Owns scenario resources during startup and transfers that ownership to the running context.
 * Shutdown attempts every resource and preserves all failures in their original order.
 */
@RequiredArgsConstructor
public final class ScenarioResources {
	private final @NotNull Duration stopTimeout;
	private final @NotNull Consumer<Boolean> runFinalizer;
	private final Map<String, ManagedProcess> processes = new LinkedHashMap<>();
	private final Map<String, AgentClient> agents = new LinkedHashMap<>();
	private final List<WorkspaceSession> workspaces = new ArrayList<>();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final PortAllocator ports = new PortAllocator();
	private @Nullable PlayerManager players;

	public int allocatePort(@NotNull String bindAddress) {
		return ports.allocate(bindAddress);
	}

	public void addProcess(@NotNull ManagedProcess process) {
		processes.put(process.name(), process);
	}

	public void addAgent(@NotNull String process, @NotNull AgentClient agent) {
		agents.put(process, agent);
	}

	public void addWorkspace(@NotNull WorkspaceSession workspace) {
		workspaces.add(workspace);
	}

	public void players(@NotNull PlayerManager players) {
		this.players = players;
	}

	public @NotNull Map<String, ManagedProcess> processes() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(processes));
	}

	public @NotNull Map<String, AgentClient> agents() {
		return Map.copyOf(agents);
	}

	/**
	 * Closes players and agents, then stops processes and finalizes workspaces in reverse order.
	 * Any cleanup failure marks the run unsuccessful so diagnostic workspaces are retained.
	 */
	public void close(boolean successful) {
		if (!closed.compareAndSet(false, true))
			return;

		List<RuntimeException> failures = new ArrayList<>();
		if (players != null)
			attempt(players::close, failures);
		for (AgentClient agent : agents.values())
			attempt(agent::close, failures);
		for (ManagedProcess process : new ArrayList<>(processes.values()).reversed())
			attempt(() -> process.stop(stopTimeout), failures);
		for (WorkspaceSession workspace : workspaces.reversed())
			attempt(() -> workspace.finish(successful && failures.isEmpty()), failures);
		attempt(() -> runFinalizer.accept(successful && failures.isEmpty()), failures);

		if (failures.isEmpty())
			return;
		RuntimeException first = failures.getFirst();
		for (RuntimeException failure : failures.subList(1, failures.size()))
			if (failure != first)
				first.addSuppressed(failure);
		throw first;
	}

	private void attempt(Runnable action, List<RuntimeException> failures) {
		try {
			action.run();
		} catch (RuntimeException exception) {
			failures.add(exception);
		}
	}
}
