package me.whereareiam.anvil.integration.intellij.scenario.editor.inspection;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the versions a platform provider on a module's classpath knows, from its {@code <platform>-versions.toml}.
 * <p>
 * The file is the one platform planning reads, so the editor agrees with the engine of the Anvil version the
 * project uses. A provider that is not on the classpath, or lists no versions, has no data.
 */
@UtilityClass
class PlatformVersions {
	private final Pattern KNOWN = Pattern.compile("(?ms)^known\\s*=\\s*\\[(.*?)]");
	private final Pattern VERIFIED = Pattern.compile("(?ms)^\\[verified]\\s*$(.*?)(?=^\\[|\\z)");
	private final Pattern VERIFIED_VERSION = Pattern.compile("(?m)^\\s*\"([^\"]+)\"\\s*=");
	private final Pattern STRING = Pattern.compile("\"([^\"]+)\"");

	/**
	 * Returns the versions the platform's provider knows, or null when the module has no data for it.
	 *
	 * @param context  element whose module's classpath is searched
	 * @param platform platform ID, such as {@code paper}
	 */
	@Nullable Set<String> known(@NotNull PsiElement context, @NotNull String platform) {
		Module module = ModuleUtilCore.findModuleForPsiElement(context);
		if (module == null) return null;

		Map<String, Optional<Set<String>>> versions = CachedValuesManager.getManager(module.getProject()).getCachedValue(module,
				() -> CachedValueProvider.Result.create(new ConcurrentHashMap<>(), ProjectRootModificationTracker.getInstance(module.getProject())));

		return versions.computeIfAbsent(platform, ignored -> Optional.ofNullable(read(module, platform))).orElse(null);
	}

	/**
	 * Parses the known and verified versions of version data, or returns null when it lists none.
	 */
	@Nullable Set<String> parse(@NotNull String toml) {
		Set<String> versions = new LinkedHashSet<>();
		strings(KNOWN.matcher(toml), versions);
		Matcher verified = VERIFIED.matcher(toml);
		if (verified.find()) {
			Matcher keys = VERIFIED_VERSION.matcher(verified.group(1));
			while (keys.find()) versions.add(keys.group(1));
		}

		return versions.isEmpty() ? null : versions;
	}

	private @Nullable Set<String> read(@NotNull Module module, @NotNull String platform) {
		String name = platform + "-versions.toml";
		String path = "me/whereareiam/anvil/platform/" + platform + "/" + name;
		var scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true);
		for (VirtualFile file : FilenameIndex.getVirtualFilesByName(name, scope)) {
			if (!file.getPath().endsWith(path)) continue;

			try {
				return parse(VfsUtilCore.loadText(file));
			} catch (IOException ignored) {
				return null;
			}
		}

		return null;
	}

	private void strings(@NotNull Matcher list, @NotNull Set<String> into) {
		if (!list.find()) return;

		Matcher value = STRING.matcher(list.group(1));
		while (value.find()) into.add(value.group(1));
	}
}
