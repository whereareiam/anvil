package me.whereareiam.anvil.environment.provisioning.artifact.api;

/**
 * Owns the transport resources used to obtain artifacts and resolution metadata.
 *
 * <p>The assembling application owns this service and closes it after all borrowers have
 * finished. Providers receive the narrower {@link ArtifactResolver} contract.</p>
 */
public interface ArtifactAcquirer extends ArtifactResolver, AutoCloseable {
	/**
	 * Releases acquisition transports after every operation has finished.
	 * Any separately supplied cache remains available to its other users.
	 */
	@Override
	void close();
}
