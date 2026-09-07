package me.whereareiam.anvil.execution.docker.image;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.execution.docker.model.Container;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Reads the JVM properties from an ephemeral container created from an image.
 */
@RequiredArgsConstructor
final class DockerJavaInspector {
    private final DockerClient client;

    String probe(String image) {
        String containerId = client.createContainerCmd(image)
                .withEntrypoint("java")
                .withCmd("-XshowSettings:properties", "-version")
                .exec()
                .getId();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Container container = new Container(client, containerId);
        RuntimeException failure = null;

        try {
            container.start();
            container.awaitExit();
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    output.writeBytes(frame.getPayload());
                }
            };
            if (!client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(callback)
                    .awaitCompletion(5, TimeUnit.SECONDS))
                throw new ProvisioningException("Timed out reading Docker Java properties");

            return output.toString(StandardCharsets.UTF_8);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure = new ProvisioningException("Interrupted inspecting Java in Docker image " + image, interrupted);
            throw failure;
        } catch (RuntimeException cause) {
            failure = new ProvisioningException("Could not inspect Java in Docker image " + image, cause);
            throw failure;
        } finally {
            try {
                container.remove();
            } catch (RuntimeException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

}
