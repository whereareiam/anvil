package me.whereareiam.anvil.provisioning.java.distribution;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.provisioning.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a Java distribution supported by the automatic local provisioner.
 */
public interface JavaDistributionDescriptor {
    /**
     * Stable distribution identifier used by {@link JavaRequirement}.
     */
    @NotNull String id();

    /**
     * Returns the Foojay catalog identifier used by this distribution.
     */
    @NotNull String catalogId();

    /**
     * Returns whether an inspected installation belongs to this distribution.
     */
    boolean matches(@NotNull JavaInstallation installation);
}
