package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debugs an Anvil run configuration: starts a new environment whose processes load a JDWP agent and shows its
 * console, and the environment opens one debug session per process as each JVM starts.
 */
public final class AnvilDebugRunner extends GenericProgramRunner<RunnerSettings> {
	@Override
	public @NotNull String getRunnerId() {
		return "AnvilDebugRunner";
	}

	@Override
	public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
		return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof AnvilRunConfiguration;
	}

	@Override
	protected @Nullable RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
			throws ExecutionException {
		ExecutionResult result = state.execute(environment.getExecutor(), this);
		if (result == null) return null;

		return new RunContentBuilder(result, environment).showRunContent(environment.getContentToReuse());
	}
}
