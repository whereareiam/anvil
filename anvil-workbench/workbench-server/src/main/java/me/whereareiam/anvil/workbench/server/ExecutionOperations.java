package me.whereareiam.anvil.workbench.server;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.type.RunningContainer;
import me.whereareiam.anvil.environment.execution.api.BoundExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import me.whereareiam.anvil.workbench.protocol.model.Handle;
import me.whereareiam.anvil.workbench.protocol.model.execution.AwaitProcess;
import me.whereareiam.anvil.workbench.protocol.model.execution.CheckProcess;
import me.whereareiam.anvil.workbench.protocol.model.execution.CloseSession;
import me.whereareiam.anvil.workbench.protocol.model.execution.CloseTarget;
import me.whereareiam.anvil.workbench.protocol.model.execution.CommandOutput;
import me.whereareiam.anvil.workbench.protocol.model.execution.ExecuteInProcess;
import me.whereareiam.anvil.workbench.protocol.model.execution.ExecutionRequest;
import me.whereareiam.anvil.workbench.protocol.model.execution.FreezeProcess;
import me.whereareiam.anvil.workbench.protocol.model.execution.OpenSession;
import me.whereareiam.anvil.workbench.protocol.model.execution.PrepareTarget;
import me.whereareiam.anvil.workbench.protocol.model.execution.PreparedTarget;
import me.whereareiam.anvil.workbench.protocol.model.execution.ProcessEnd;
import me.whereareiam.anvil.workbench.protocol.model.execution.ProcessLiveness;
import me.whereareiam.anvil.workbench.protocol.model.execution.StartProcess;
import me.whereareiam.anvil.workbench.protocol.model.execution.TerminateProcess;
import me.whereareiam.anvil.workbench.protocol.model.execution.ThawProcess;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Answers an engine's requests about execution sessions, prepared processes and running processes.
 */
@RequiredArgsConstructor
final class ExecutionOperations {
	private final @NotNull Workbench workbench;
	private final @NotNull Handles handles;
	private final @NotNull String owner;

	@NotNull Object handle(@NotNull ExecutionRequest<?> request) throws Exception {
		return switch (request) {
			case OpenSession open -> open(open);
			case CloseSession close -> {
				ExecutionSession session = handles.get(owner, close.getHandle(), ExecutionSession.class);
				handles.remove(owner, close.getHandle());
				session.close();
				yield new Done();
			}
			case PrepareTarget prepare -> prepare(prepare);
			case CloseTarget close -> {
				ProcessTarget target = handles.get(owner, close.getHandle(), ProcessTarget.class);
				handles.remove(owner, close.getHandle());
				target.close();
				yield new Done();
			}
			case StartProcess start -> {
				ProcessExecution execution = handles.get(owner, start.getTarget(), ProcessTarget.class).start(start.getLaunch());
				yield new Handle(handles.register(owner, execution, () -> execution.terminate(true)));
			}
			case CheckProcess check -> new ProcessLiveness(execution(check.getHandle()).isAlive());
			case AwaitProcess await -> new ProcessEnd(execution(await.getHandle()).await(Duration.ofMillis(await.getTimeoutMillis())));
			case TerminateProcess terminate -> {
				execution(terminate.getHandle()).terminate(terminate.isForce());
				yield new Done();
			}
			case FreezeProcess freeze -> {
				execution(freeze.getHandle()).freeze();
				yield new Done();
			}
			case ThawProcess thaw -> {
				execution(thaw.getHandle()).thaw();
				yield new Done();
			}
			case ExecuteInProcess execute -> {
				RunningContainer.CommandResult result = execution(execute.getHandle())
						.execute(Duration.ofMillis(execute.getTimeoutMillis()), execute.getCommand().toArray(String[]::new));
				yield new CommandOutput(result.exitCode(), result.output());
			}
		};
	}

	private Handle open(OpenSession open) {
		BoundExecutionProvider provider = workbench.executions().get(open.getProvider());
		if (provider == null)
			throw new IllegalArgumentException("The workbench offers no execution provider '" + open.getProvider() + "'");

		ExecutionSession session = provider.open(open.getContext());
		return new Handle(handles.register(owner, session, session::close));
	}

	/**
	 * Prepares a process; one that runs no agent, such as a container, is reported without agent addresses.
	 */
	private PreparedTarget prepare(PrepareTarget prepare) {
		ProcessTarget target = handles.get(owner, prepare.getSession(), ExecutionSession.class).prepare(prepare.getRequest());
		String handle = handles.register(owner, target, target::close);
		try {
			return new PreparedTarget(handle, target.address(), target.peerAddress(), target.bindAddress(), target.agentAddress(),
					target.agentPort(), target.agentBindAddress(), null, target.listeners(), target.peerListeners());
		} catch (UnsupportedOperationException noAgent) {
			return new PreparedTarget(handle, target.address(), target.peerAddress(), target.bindAddress(), null, 0, null,
					noAgent.getMessage(), target.listeners(), target.peerListeners());
		}
	}

	private ProcessExecution execution(String handle) {
		return handles.get(owner, handle, ProcessExecution.class);
	}
}
