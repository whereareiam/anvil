package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.runner.RunnerSession;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.runner.protocol.ToolingProtocol;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * Project-classpath entry point for structured IDE sessions over private standard streams.
 * Standard output is reserved for protocol frames; library diagnostics use standard error.
 */
public final class AnvilTooling {
	/**
	 * Evaluates direct scenario definitions from the supplied class names and serves requests until
	 * input closes. When no names are supplied, the generated index and Java service descriptor are
	 * discovered from the project runtime classpath.
	 *
	 * @param definitions configured definition class names
	 * @throws Exception when configuration or definition evaluation fails
	 */
	public static void main(@NotNull String[] definitions) throws Exception {
		PrintWriter protocolOutput = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
		System.setOut(System.err);
		var repository = ScenarioRepository.load(List.of(definitions));

		try (RunnerSession session = new RunnerSession(
				() -> AnvilLauncher.create(engineOptions(System.getProperties())),
				repository
		)) {
			new ToolingProtocol(session, new InputStreamReader(System.in, StandardCharsets.UTF_8), protocolOutput).run();
		}
	}

	static @NotNull EngineOptions engineOptions(@NotNull Properties properties) {
		Properties snapshot = new Properties();
		for (String name : properties.stringPropertyNames()) {
			snapshot.setProperty(name, properties.getProperty(name));
		}

		snapshot.putIfAbsent(EngineProperties.CONSOLE_COLORS_PROPERTY, "true");
		return EngineProperties.from(snapshot);
	}
}
