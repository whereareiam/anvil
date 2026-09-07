package me.whereareiam.anvil.execution.local;

import me.whereareiam.anvil.execution.api.ExecutionProvider;
import me.whereareiam.anvil.execution.api.ExecutionSession;
import me.whereareiam.anvil.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Executes scenario workloads directly on the current host.
 */
public final class LocalExecutionProvider implements ExecutionProvider {
	private final @NotNull LocalExecutionSettings settings;
	private final @Nullable JavaProvisioner java;

    public LocalExecutionProvider() {
        this(LocalExecutionSettings.builder().build(), null);
    }

    public LocalExecutionProvider(@NotNull JavaProvisioner java) {
        this(LocalExecutionSettings.builder().build(), java);
    }

	public LocalExecutionProvider(@NotNull LocalExecutionSettings settings, @Nullable JavaProvisioner java) {
        this.settings = settings;
        this.java = java;
    }

    @Override
    public @NotNull String id() {
        return "local";
    }

    @Override
    public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
        JavaProvisioner provisioner = java;
        if (provisioner == null && context.getJavaValidator() instanceof JavaProvisioner supplied)
            provisioner = supplied;

        if (provisioner == null) throw new IllegalStateException("Local execution requires a Java provisioner");
        return new LocalExecutionSession(context, settings, provisioner);
    }
}
