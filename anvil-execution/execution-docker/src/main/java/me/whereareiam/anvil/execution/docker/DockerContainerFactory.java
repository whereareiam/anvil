package me.whereareiam.anvil.execution.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.execution.docker.model.ContainerConfiguration;
import me.whereareiam.anvil.execution.docker.model.Container;
import me.whereareiam.anvil.execution.docker.container.ContainerAttachment;
import me.whereareiam.anvil.api.exception.ProvisioningException;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Creates container handles from Anvil-owned immutable container specifications.
 */
@RequiredArgsConstructor
public final class DockerContainerFactory {
    private final DockerClient client;

    /**
     * Creates a stopped container and returns its lifecycle handle.
     */
    public Container create(ContainerConfiguration configuration) {
        ExposedPort game = ExposedPort.tcp(25565);
        ExposedPort agent = ExposedPort.tcp(25566);
        Ports bindings = new Ports();
        List<ExposedPort> exposed = new ArrayList<>();

        if (configuration.getPorts().getGame() != null) {
            bindings.bind(game, Ports.Binding.bindIpAndPort(configuration.getPorts().getGame().getHostString(),
                    configuration.getPorts().getGame().getPort()));
            exposed.add(game);
        }
        if (configuration.getPorts().getAgent() != null) {
            bindings.bind(agent, Ports.Binding.bindIpAndPort(configuration.getPorts().getAgent().getHostString(),
                    configuration.getPorts().getAgent().getPort()));
            exposed.add(agent);
        }

        HostConfig host = HostConfig.newHostConfig()
                .withNetworkMode(configuration.getNetwork())
                .withPortBindings(bindings)
                .withBinds(new Bind(configuration.getWorkspace().toAbsolutePath().toString(), new Volume("/workspace")),
                        new Bind(configuration.getJar().toAbsolutePath().toString(), new Volume("/anvil/application.jar")))
                .withAutoRemove(false);

        String id = client.createContainerCmd(configuration.getImage())
                .withName(configuration.getName())
                .withHostConfig(host)
                .withAliases(configuration.getAlias())
                .withExposedPorts(exposed.toArray(ExposedPort[]::new))
                .withWorkingDir("/workspace")
                .withEntrypoint("java")
                .withEnv(configuration.getEnvironment().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toList())
                .withCmd(configuration.getCommand())
                .exec()
                .getId();

        return new Container(client, id);
    }

    /** Attaches process streams to a container and starts its exit watcher. */
    public ContainerAttachment attach(Container container) {
        ContainerAttachment attachment = null;
        try {
            PipedInputStream output = new PipedInputStream(64 * 1024);
            PipedOutputStream outputWriter = new PipedOutputStream(output);
            PipedInputStream input = new PipedInputStream(8 * 1024);
            PipedOutputStream inputWriter = new PipedOutputStream(input);

            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    try {
                        outputWriter.write(frame.getPayload());
                        outputWriter.flush();
                    } catch (IOException failure) {
                        throw new ProvisioningException("Could not forward Docker output", failure);
                    }
                }
            };

            attachment = new ContainerAttachment(output, outputWriter, input, inputWriter, callback);
            client.attachContainerCmd(container.id())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withStdIn(input)
                    .withFollowStream(true)
                    .exec(callback);

            container.start();
            ContainerAttachment attached = attachment;
            CompletableFuture.runAsync(() -> {
                try {
                    container.awaitExit();
                    attached.exited().complete(null);
                } catch (RuntimeException failure) {
                    attached.exited().completeExceptionally(failure);
                } finally {
                    attached.close();
                }
            });

            return attached;
        } catch (IOException | RuntimeException failure) {
            if (attachment != null) attachment.close();
            throw new ProvisioningException("Could not attach Docker container", failure);
        }
    }
}
