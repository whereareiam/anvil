package me.whereareiam.anvil.api.model.workspace;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Copies one local or named artifact source into a process workspace.
 *
 * <p>Directory sources copy their contents into {@link #target}; file sources are copied to the
 * target file itself.</p>
 */
@Value
@Builder(toBuilder = true)
public class WorkspaceAsset {
	@NotNull
	@Builder.Default
	String group = "default";

	@NotNull AssetSource source;
	@NotNull Path target;

	@NotNull
	@Builder.Default
	AssetInstallMode mode = AssetInstallMode.ALWAYS;
}
