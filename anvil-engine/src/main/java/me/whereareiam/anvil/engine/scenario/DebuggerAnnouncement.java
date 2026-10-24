package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.api.engine.EngineExtension;
import me.whereareiam.anvil.api.engine.EngineRegistration;
import me.whereareiam.anvil.api.process.RunningProcess;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.net.InetSocketAddress;

/**
 * Tells the person running a scenario where to attach a debugger: once a scenario is ready, every process that
 * accepts one names the address of its {@value #LISTENER} listener.
 */
public final class DebuggerAnnouncement implements EngineExtension {
	private static final String LISTENER = "debug";

	private final @NotNull PrintStream output;

	/**
	 * Creates an announcement that prints to the given stream.
	 *
	 * @param output where the addresses are printed, normally standard error
	 */
	public DebuggerAnnouncement(@NotNull PrintStream output) {
		this.output = output;
	}

	@Override
	public void install(@NotNull EngineRegistration registration) {
		registration.scenarios(scenario -> {
			for (RunningProcess process : scenario.processes().all()) {
				InetSocketAddress address;
				try {
					address = process.listener(LISTENER);
				} catch (IllegalArgumentException notDebugged) {
					continue;
				}
				output.println("[Anvil] Debugger of '" + process.name() + "' in scenario '" + scenario.definition().getName()
						+ "': attach a remote JVM debugger to " + address.getHostString() + ":" + address.getPort());
			}
			return successful -> {
			};
		});
	}
}
