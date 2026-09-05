package me.whereareiam.anvil.api.model.workspace;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CacheIdentity;
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
	 * Controls cache invalidation when installed assets change. Process-only identity is an
	 * explicit opt-in for independently validated caches, such as Maven artifact repositories.
	 */
	@NotNull
	@Builder.Default
	CacheIdentity identity = CacheIdentity.PROCESS_AND_ASSETS;

	/**
	 * Returns the user-visible cache key or a stable path-based fallback.
	 *
	 * @return effective cache key
	 */
	public @NotNull String effectiveKey() {
		return key == null || key.isBlank() ? group + ":" + path : key;
	}
}
