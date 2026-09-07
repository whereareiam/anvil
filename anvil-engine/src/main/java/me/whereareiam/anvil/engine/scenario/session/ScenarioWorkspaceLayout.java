package me.whereareiam.anvil.engine.scenario.session;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.engine.provisioning.workspace.WorkspaceFiles;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Calculates the run and process workspace locations for one scenario session.
 */
@RequiredArgsConstructor
final class ScenarioWorkspaceLayout {
    private final EngineOptions options;
    private final AnvilScenario scenario;
    private final Path runDirectory;

    static @NotNull ScenarioWorkspaceLayout create(
            @NotNull EngineOptions options,
            @NotNull AnvilScenario scenario
    ) {
        String runId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path root = options.getWorkDirectory().toAbsolutePath().normalize();
        Path directory = root.resolve(WorkspaceFiles.safeName(scenario.getName())).resolve(runId);

        return new ScenarioWorkspaceLayout(options, scenario, directory);
    }

    @NotNull Path runDirectory() {
        return runDirectory;
    }

    @NotNull Path processDirectory(@NotNull MinecraftProcess process) {
        if (process.getWorkspace().getMode() != WorkspaceMode.PERSISTENT)
            return runDirectory.resolve(WorkspaceFiles.safeName(process.getName()));

        return options.getWorkDirectory().toAbsolutePath().normalize()
                .resolve(WorkspaceFiles.safeName(scenario.getName())).resolve("persistent")
                .resolve(WorkspaceFiles.safeName(process.getName()));
    }
}
