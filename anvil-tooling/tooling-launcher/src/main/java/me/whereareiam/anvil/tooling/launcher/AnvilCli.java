package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.engine.config.EngineProperties;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.runner.AnvilRunner;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Starts the foreground CLI with system-property configuration and the default Anvil engine.
 */
public final class AnvilCli {
	/**
	 * Binds the current process streams and creates the engine only when a scenario is started.
	 *
	 * @param arguments direct scenario selection options; definitions are discovered from the runtime index
	 * @throws Exception when configuration, definition loading, or scenario execution fails
	 */
	public static void main(@NotNull String[] arguments) throws Exception {
		var options = EngineProperties.fromSystemProperties();

		new AnvilRunner(
				new InputStreamReader(System.in, StandardCharsets.UTF_8),
				new PrintWriter(System.out, true),
				() -> AnvilLauncher.create(options)
		).run(arguments);
	}
}
