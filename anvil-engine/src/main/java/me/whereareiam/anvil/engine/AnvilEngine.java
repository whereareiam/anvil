package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.engine.EngineDefaults;
import me.whereareiam.anvil.engine.provisioning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.scenario.ScenarioRun;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts reproducible local Minecraft scenarios and creates real protocol players on demand.
 *
 * <pre>{@code
 * try (AnvilEngine engine = new AnvilEngine(options);
 *      AnvilContext context = engine.start(scenario)) {
 *     SimulatedPlayer alice = context.players().create("Alice");
 *     Session session = alice.capability(Session.class);
 *     session.connect();
 *     session.connected(Duration.ofSeconds(30));
 * }
 * }</pre>
 */
public final class AnvilEngine implements ScenarioEngine {
	private final EngineOptions options;
	private final EngineProviders providers;
	private final ScenarioArtifactResolver artifacts;

	private final List<ScenarioRun> contexts = new ArrayList<>();

	private @Nullable ProtocolBackend protocolBackend;
	private boolean closed;

	/**
	 * Discovers installed providers and prepares scenario orchestration without starting a backend.
	 *
	 * @param options immutable engine configuration
	 */
	public AnvilEngine(@NotNull EngineOptions options) {
		this.options = EngineDefaults.resolve(options);
		this.providers = new EngineProviders(this.options);
		this.artifacts = new ScenarioArtifactResolver(this.options.getArtifacts(), providers.getAgentArtifacts());
	}

	/**
	 * Starts a scenario and retains it only after startup and its setup hook succeed.
	 *
	 * @param scenario scenario definition
	 * @return running scenario context
	 * @throws IllegalStateException if this engine is closed
	 */
	@Override
	public synchronized @NotNull AnvilContext start(@NotNull AnvilScenario scenario) {
		if (closed) throw new IllegalStateException("Cannot start a scenario after the engine is closed");

		ScenarioRun context = ScenarioRun.builder()
				.options(options)
				.scenario(scenario)
				.providers(providers.getPlatforms())
				.playerComposer(providers.getPlayerComposer())
				.artifacts(artifacts)
				.agentConnections(providers.getAgentConnections())
				.backend(this::protocolBackend)
				.start();
		contexts.add(context);

		return context;
	}

	private @NotNull ProtocolBackend protocolBackend() {
		if (protocolBackend == null) protocolBackend = providers.getProtocol().create(options.getCacheDirectory());
		return protocolBackend;
	}

	/**
	 * Closes every retained context before the shared backend, attempting all cleanup operations.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;

		List<Throwable> failures = new ArrayList<>();
		contexts.forEach(context -> closeResource(context::close, failures));
		contexts.clear();
		if (protocolBackend != null) closeResource(protocolBackend::close, failures);
		if (failures.isEmpty()) return;

		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);

		if (first instanceof Error error) throw error;
		throw (RuntimeException) first;
	}

	private void closeResource(Runnable close, List<Throwable> failures) {
		try {
			close.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
}
