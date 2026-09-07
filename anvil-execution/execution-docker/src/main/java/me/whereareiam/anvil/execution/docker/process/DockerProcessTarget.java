package me.whereareiam.anvil.execution.docker.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.execution.api.model.JavaCommand;
import me.whereareiam.anvil.execution.api.model.ProcessRequest;
import me.whereareiam.anvil.execution.docker.model.Container;
import me.whereareiam.anvil.execution.docker.model.ContainerConfiguration;
import me.whereareiam.anvil.execution.docker.DockerContainerFactory;
import me.whereareiam.anvil.execution.docker.model.PortBindings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns successive containers sharing the same workspace and network endpoints.
 */
@RequiredArgsConstructor
public final class DockerProcessTarget implements ProcessTarget {
    private final DockerContainerFactory docker;

    private final String network;
    private final String alias;
    private final String image;
    private final ProcessRequest request;
    private final InetSocketAddress address;
    private final InetSocketAddress agentAddress;

    private @Nullable Container container;

    @Override
    public synchronized @NotNull ProcessExecution start(@NotNull JavaCommand command) {
        close();

        PortBindings ports = PortBindings.builder()
                .game(request.isPublishGame() ? address : null)
                .agent(request.isAgent() ? agentAddress : null)
                .build();

        Container created = docker.create(ContainerConfiguration.builder()
                .name("anvil-process-" + UUID.randomUUID())
                .network(network)
                .alias(alias)
                .image(image)
                .workspace(request.getWorkspace())
                .jar(command.getJar())
                .ports(ports)
                .command(javaArguments(command))
                .environment(command.getEnvironment())
                .build());

        container = created;
        return new DockerProcess(docker, created);
    }

    private List<String> javaArguments(JavaCommand command) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-Xms256m");
        arguments.add("-Xmx" + command.getMemoryMegabytes() + "m");
        arguments.addAll(command.getJvmArguments());
        arguments.add("-jar");
        arguments.add("/anvil/application.jar");
        arguments.addAll(command.getArguments());

        return arguments;
    }

    @Override
    public @NotNull InetSocketAddress address() {
        return address;
    }

    @Override
    public @NotNull InetSocketAddress peerAddress() {
        return InetSocketAddress.createUnresolved(alias, 25565);
    }

    @Override
    public @NotNull String bindAddress() {
        return "0.0.0.0";
    }

    @Override
    public @NotNull InetSocketAddress agentAddress() {
        return agentAddress;
    }

    @Override
    public int agentPort() {
        return 25566;
    }

    @Override
    public @NotNull String agentBindAddress() {
        return "0.0.0.0";
    }

    @Override
    public synchronized void close() {
        if (container == null) return;
        container.remove();
        container = null;
    }
}
