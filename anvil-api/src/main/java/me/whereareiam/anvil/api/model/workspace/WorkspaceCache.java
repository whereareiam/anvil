package me.whereareiam.anvil.api.model.workspace;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.CachePolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Declares a workspace-relative file or directory that may be restored and saved across runs.
 */
@Value
@Builder(toBuilder = true)
public class WorkspaceCache {
	@NotNull
	@Builder.Default
	String group = "default";
	@NotNull Path path;
	@NotNull
	@Builder.Default
	CachePolicy policy = CachePolicy.RESTORE_AND_SAVE;
	@Nullable String key;

	/**
	 * Returns the user-visible cache key or a stable path-based fallback.
	 *
	 * @return effective cache key
	 */
	public @NotNull String effectiveKey() {
		return key == null || key.isBlank() ? group + ":" + path : key;
	}
}
