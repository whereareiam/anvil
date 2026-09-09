package me.whereareiam.anvil.environment.execution.docker.image;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Translates Docker image operations without exposing docker-java types to image policy code.
 */
public final class DockerImageClient {
	private final DockerClient client;
	private final DockerJavaInspector javaInspector;

	public DockerImageClient(DockerClient client) {
		this.client = client;
		javaInspector = new DockerJavaInspector(client);
	}

	public void pull(String image) {
		try {
			if (!client.pullImageCmd(image).start().awaitCompletion(5, TimeUnit.MINUTES))
				throw new ProvisioningException("Timed out pulling Docker image " + image);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted pulling Docker image " + image, failure);
		}
	}

	public DockerImageMetadata inspectMetadata(String image) {
		try {
			InspectImageResponse response = client.inspectImageCmd(image).exec();
			return new DockerImageMetadata(response.getId(), response.getRepoDigests());
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Could not inspect Docker image " + image, failure);
		}
	}

	public String probeJavaRuntime(String image) {
		return javaInspector.probe(image);
	}

	@RequiredArgsConstructor
	public static final class DockerImageMetadata {
		private final String id;
		private final List<String> repoDigests;

		public String id() {
			return id;
		}

		public List<String> repoDigests() {
			return repoDigests;
		}
	}
}
