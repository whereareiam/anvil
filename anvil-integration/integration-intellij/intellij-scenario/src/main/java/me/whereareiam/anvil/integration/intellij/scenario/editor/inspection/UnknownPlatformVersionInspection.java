package me.whereareiam.anvil.integration.intellij.scenario.editor.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;

import java.util.Set;

import me.whereareiam.anvil.integration.intellij.scenario.editor.BuilderCalls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports a distribution version that the selected platform's provider does not know.
 * <p>
 * Reads {@code Distribution.remote(version, build)} and {@code Distribution.pinned(version, sha256)} passed to a
 * server or proxy builder whose platform is a constant. Planning runs such a version as untested, and the strict
 * support policy refuses it; usually it is a typo or a version the project's Anvil release does not cover yet.
 */
public final class UnknownPlatformVersionInspection extends AbstractBaseJavaLocalInspectionTool {
	private static final Set<String> FACTORY = Set.of(BuilderCalls.DISTRIBUTION);
	private static final Set<String> PROCESSES = Set.of(BuilderCalls.SERVER, BuilderCalls.PROXY);

	@Override
	public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
		return new JavaElementVisitor() {
			@Override
			public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
				if (!BuilderCalls.calls(call, FACTORY, Set.of("remote", "pinned"))) return;

				String version = BuilderCalls.argument(call, 0);
				String platform = platform(call);
				if (version == null || platform == null) return;

				Set<String> known = PlatformVersions.known(call, platform);
				if (known == null || known.contains(version)) return;

				holder.registerProblem(call.getArgumentList().getExpressions()[0],
						"Anvil does not know " + platform + " " + version + "; it runs untested, and the strict support policy refuses it");
			}
		};
	}

	private static @Nullable String platform(@NotNull PsiMethodCallExpression distribution) {
		PsiMethodCallExpression owner = BuilderCalls.argumentOf(distribution);
		if (owner == null || !BuilderCalls.calls(owner, PROCESSES, Set.of("distribution"))) return null;

		for (PsiMethodCallExpression call : BuilderCalls.chain(owner))
			if (BuilderCalls.calls(call, PROCESSES, Set.of("platform"))) return BuilderCalls.argument(call, 0);

		return null;
	}
}
