package me.whereareiam.anvil.capability.dispatch;

import me.whereareiam.anvil.api.process.ProcessCapability;
import org.jetbrains.annotations.NotNull;

/**
 * Dispatches commands as the console of a server or proxy, through the platform's own command dispatcher, and tells
 * whether the platform accepted them. It belongs to the logical process and does not require a simulated player. The
 * process's own input and log are {@link me.whereareiam.anvil.api.process.RunningProcess#console()}, which types into
 * the process and reads what it prints.
 *
 * <pre>{@code
 * Dispatch dispatch = context.processes().server("lobby").capability(Dispatch.class);
 * assertTrue(dispatch.execute("reload"));
 * }</pre>
 */
public interface Dispatch extends ProcessCapability {
	/**
	 * Dispatches a command as the owning process's console.
	 *
	 * @param command command without a leading slash
	 * @return whether the platform accepted the command
	 */
	boolean execute(@NotNull String command);
}
