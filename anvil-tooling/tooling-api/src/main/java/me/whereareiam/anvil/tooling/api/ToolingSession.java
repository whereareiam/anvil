package me.whereareiam.anvil.tooling.api;

import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Owns an interactive environment and the engine resources used across its replacements.
 * Closing this owner is terminal; stopping an environment permits another start.
 * Commands acknowledge submission, not successful in-game execution.
 */
public interface ToolingSession extends AutoCloseable {
	/**
	 * Version of the framed local session protocol. Version 7 maps shared immutable payload models
	 * directly and represents an action target as an object containing its type and name.
	 * Both peers must use this version before submitting component or scenario actions.
	 * <p>
	 * A failed request is answered by a failed response carrying the request ID. An {@code error} frame
	 * means the runner can no longer serve the connection, for example after a request without a usable ID;
	 * clients close the connection when they receive one. Transient problems, such as one failed snapshot
	 * poll, are reported as diagnostics on the runner's standard error instead.
	 */
	int PROTOCOL_VERSION = 7;

	/**
	 * Lists discovered scenarios and their declared process topology without creating an engine,
	 * resolving distributions, or starting Minecraft processes.
	 *
	 * @return descriptors with definition-class identities
	 */
	@NotNull List<ScenarioDescriptor> scenarios();

	/**
	 * Replaces the current environment after complete cleanup. The selection is the fully qualified
	 * scenario-definition class name; implementations may also accept a unique scenario name as a
	 * convenience.
	 *
	 * @param definition definition class name or an unambiguous scenario name
	 * @param process initial process to start, or null to start the whole scenario and its setup
	 */
	void start(@NotNull String definition, @Nullable String process);

	/**
	 * Starts remaining processes and completes scenario setup once, preserving the current run.
	 */
	void startAll();

	/**
	 * Starts one prepared process while retaining its original wiring and workspace.
	 *
	 * @param process stable process name
	 */
	void startProcess(@NotNull String process);

	/**
	 * Stops one process while preserving its workspace, addresses, and the other processes.
	 *
	 * @param process stable process name
	 */
	void stopProcess(@NotNull String process);

	/**
	 * Returns the latest session and target state without waiting for startup.
	 *
	 * @return current or retained final state
	 */
	@NotNull SessionSnapshot snapshot();

	/**
	 * Sends a single console command to a ready process.
	 *
	 * @param process stable process name
	 * @param command command without a leading slash or newline
	 */
	void console(@NotNull String process, @NotNull String command);

	/**
	 * Invokes a contributed action after validating its session, target, availability, and inputs.
	 *
	 * @param request explicit action and target selection for the current environment
	 * @return portable action outcome
	 */
	@NotNull ActionResult invoke(@NotNull ActionRequest request);

	/**
	 * Restarts a process with its workspace and address, retaining other processes.
	 *
	 * @param process stable process name
	 */
	void restartProcess(@NotNull String process);

	/**
	 * Returns retained console tail output without consuming the live event stream.
	 * Output remains available after the environment stops until the next start.
	 *
	 * @param process stable process name
	 * @param maximumLines positive tail limit
	 * @return captured output in chronological order
	 */
	@NotNull List<String> logs(@NotNull String process, int maximumLines);

	/**
	 * Reads unseen bounded console history for this session's single event consumer.
	 * Process generations have independent sequence numbers; evictions produce an explicit gap line.
	 *
	 * @return newly captured lines in process order
	 */
	@NotNull List<LogEvent> drainLogs();

	/**
	 * Stops the current environment, retaining final state and output for inspection.
	 */
	void stop();

	/**
	 * Stops the environment and closes owned engine resources. Repeated calls have no effect.
	 */
	@Override
	void close();
}
