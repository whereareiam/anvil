package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import me.whereareiam.anvil.execution.api.ExecutionProvider;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import me.whereareiam.anvil.engine.provisioning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.scenario.session.ScenarioSession;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Starts reproducible local Minecraft scenarios and creates real protocol players on demand.
 *
 * <pre>{@code
 * try (AnvilEngine engine = new AnvilEngine(options);
 *      ScenarioContext context = engine.start(scenario)) {
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
	private final ArtifactStore downloads;
	private final JavaProvisioner javaProvisioner;

	private final List<ScenarioSession> sessions = new ArrayList<>();

	private @Nullable ProtocolBackend protocolBackend;
	private boolean closed;

	/**
	 * Discovers installed providers and prepares scenario orchestration without starting a backend.
	 *
	 * @param options immutable engine configuration
	 */
	public AnvilEngine(@NotNull EngineOptions options, @NotNull ArtifactStore downloads, @NotNull JavaProvisioner java) {
		this(options, downloads, java, List.of());
	}

	public AnvilEngine(@NotNull EngineOptions options, @NotNull ArtifactStore downloads,
			@NotNull JavaProvisioner java, @NotNull Collection<ExecutionProvider> executionProviders) {
		this.downloads = downloads;
		this.javaProvisioner = java;
		this.options = EngineDefaults.resolve(options);
		this.providers = new EngineProviders(this.options, executionProviders);
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
	public synchronized @NotNull ScenarioContext start(@NotNull AnvilScenario scenario) {
		if (closed) throw new IllegalStateException("Cannot start a scenario after the engine is closed");

		ScenarioSession session = ScenarioSession.builder()
				.options(options)
				.downloads(downloads)
				.java(javaProvisioner)
				.execution(providers.execution(scenario.getExecution() == null
						? options.getExecutionId()
						: scenario.getExecution())
				)
				.scenario(scenario)
				.providers(providers.getPlatforms())
				.playerComposer(providers.getPlayerComposer())
				.artifacts(artifacts)
				.agentConnections(providers.getAgentConnections())
				.backend(this::protocolBackend)
				.start();
		sessions.add(session);

		return session;
	}

	private @NotNull ProtocolBackend protocolBackend() {
		if (protocolBackend == null) protocolBackend = providers.getProtocol().create(options.getCacheDirectory(), downloads);
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
		sessions.forEach(session -> closeResource(session::close, failures));
		sessions.clear();

		if (protocolBackend != null) closeResource(protocolBackend::close, failures);

		closeResource(downloads::close, failures);
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
