package me.whereareiam.anvil.environment.execution.docker.image;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.JavaVersionMismatchException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.environment.execution.api.image.ImageLease;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.docker.execution.DockerExecutionSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Retains immutable image selection and validates the JVM actually present inside the image.
 */
@RequiredArgsConstructor
public final class DockerImageResolver {
	private final ExecutionContext context;
	private final DockerImageClient docker;
	private final DockerExecutionSettings settings;

	public String resolve(ProcessRequest request) {
		String requested = settings.image(request.getJavaSelection().getRequirement());
		String key = UUID.nameUUIDFromBytes(requested.getBytes(StandardCharsets.UTF_8)).toString();
		Path directory = context.getCacheDirectory().resolve("docker-images").resolve(key);

		try (ImageLease ignored = context.getImageLocks().acquire(directory)) {
			Path lock = directory.resolve("image");
			String reference = !context.isRefresh() && Files.isRegularFile(lock)
					? Files.readString(lock)
					: requested;

			DockerImageClient.DockerImageMetadata image = obtain(reference);
			String id = image.id();
			Files.createDirectories(directory);
			Path properties = directory.resolve(id.substring("sha256:".length()) + ".properties");

			String details = Files.isRegularFile(properties)
					? Files.readString(properties)
					: docker.probeJavaRuntime(id);

			validate(details, request, requested);
			Files.writeString(properties, details);

			String immutable = image.repoDigests().isEmpty()
					? id
					: image.repoDigests().getFirst();

			Files.writeString(lock, immutable);

			return id;
		} catch (IOException failure) {
			throw new ProvisioningException("Could not retain Docker Java selection " + requested, failure);
		}
	}

	/**
	 * Validates the Java an image ships. The image comes from the Docker execution settings, so an image that
	 * ships another Java feature version names the mapping to change rather than local Java sources; any other
	 * failure, such as probe output that cannot be read, is reported unchanged.
	 */
	void validate(String details, ProcessRequest request, String image) {
		try {
			context.getRuntimeValidator().validate(details, request);
		} catch (JavaVersionMismatchException mismatch) {
			JavaRequirement requirement = request.getJavaSelection().getRequirement();
			String key = settings.key(requirement);
			throw new ProvisioningException(mismatch.getMessage() + "; the Docker execution settings map " + key
					+ " to image " + image + ", so map " + key + " to an image that ships Java "
					+ requirement.getFeatureVersion(), mismatch);
		}
	}

	private DockerImageClient.DockerImageMetadata obtain(String reference) {
		if (context.isRefresh() && !reference.startsWith("sha256:")) docker.pull(reference);

		try {
			return docker.inspectMetadata(reference);
		} catch (ProvisioningException missing) {
			if (context.isOffline())
				throw new ProvisioningException("Docker image is unavailable offline: " + reference, missing);
			docker.pull(reference);
			return docker.inspectMetadata(reference);
		}
	}

}
