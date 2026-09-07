package me.whereareiam.anvil.engine;

import lombok.Getter;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.agent.api.transport.AgentArtifactLocator;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.execution.api.ExecutionProvider;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Discovers the installed services and resolves one protocol before composing player capabilities.
 * Backend creation remains lazy and owned by the engine.
 */
@Getter
final class EngineProviders {
    private final Map<String, ExecutionProvider> executions;
    private final ProtocolProvider protocol;
    private final ProtocolPlayerComposer playerComposer;
    private final Map<String, PlatformProvider> platforms;
    private final AgentConnectionProvider agentConnections;
    private final AgentArtifactLocator agentArtifacts;

    EngineProviders(@NotNull EngineOptions options) {
        this(options, List.of());
    }

    EngineProviders(@NotNull EngineOptions options, @NotNull Collection<ExecutionProvider> supplied) {
        Map<String, ExecutionProvider> selected = new LinkedHashMap<>();

        ServiceLoader.load(ExecutionProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .forEach(provider -> selected.put(provider.id(), provider));

        supplied.forEach(provider -> selected.put(provider.id(), provider));

        executions = Map.copyOf(selected);
        protocol = ProtocolProviderRegistry.discover().select(options.getProtocolId());
        playerComposer = ProtocolPlayerComposer.discover(protocol.id());
        platforms = discoverPlatforms();
        agentConnections = discoverService(AgentConnectionProvider.class, "agent connection provider");
        agentArtifacts = discoverService(AgentArtifactLocator.class, "agent artifact locator");
    }

    ExecutionProvider execution(String id) {
        ExecutionProvider provider = executions.get(id);
        if (provider == null)
            throw new IllegalArgumentException("No execution provider '" + id + "'. Available: " + executions.keySet());

        return provider;
    }

    private static Map<String, PlatformProvider> discoverPlatforms() {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        List<PlatformProvider> discovered = contextLoader == null
                ? List.of()
                : ServiceLoader.load(PlatformProvider.class, contextLoader).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        if (discovered.isEmpty())
            discovered = ServiceLoader.load(PlatformProvider.class, EngineProviders.class.getClassLoader()).stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();

        return discovered.stream()
                .collect(Collectors.toUnmodifiableMap(PlatformProvider::id, provider -> provider));
    }

    private static <T> @NotNull T discoverService(@NotNull Class<T> type, @NotNull String description) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        T service = contextLoader == null
                ? null
                : ServiceLoader.load(type, contextLoader).findFirst().orElse(null);

        if (service != null) return service;
        return ServiceLoader.load(type, EngineProviders.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Anvil " + description + " is installed"));
    }

}
