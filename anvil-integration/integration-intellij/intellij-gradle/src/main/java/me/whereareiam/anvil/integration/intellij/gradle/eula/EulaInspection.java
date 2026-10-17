package me.whereareiam.anvil.integration.intellij.gradle.eula;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Reports a Gradle build script that applies an Anvil plugin without accepting the Minecraft EULA.
 * <p>
 * Without {@code anvil { acceptEula() }}, every scenario that starts a server fails before launching it, both in
 * Gradle tasks and in the IDE. The script is read as text, so Kotlin and Groovy scripts are both covered; a plugin
 * applied by a convention plugin elsewhere is not seen.
 */
public final class EulaInspection extends LocalInspectionTool {
	private static final Set<String> SCRIPTS = Set.of("build.gradle.kts", "build.gradle");
	private static final Pattern PLUGIN = Pattern.compile("id\\s*\\(?\\s*[\"']me\\.whereareiam\\.anvil(\\.junit)?[\"']");

	@Override
	public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
		if (!SCRIPTS.contains(file.getName())) return ProblemDescriptor.EMPTY_ARRAY;

		String text = file.getText();
		Matcher plugin = PLUGIN.matcher(text);
		if (!plugin.find() || text.contains("acceptEula")) return ProblemDescriptor.EMPTY_ARRAY;

		return new ProblemDescriptor[] {manager.createProblemDescriptor(
				file,
				new TextRange(plugin.start(), plugin.end()),
				"Servers do not start until the build accepts the Minecraft EULA",
				ProblemHighlightType.WARNING,
				isOnTheFly,
				new AcceptEulaFix()
		)};
	}
}
