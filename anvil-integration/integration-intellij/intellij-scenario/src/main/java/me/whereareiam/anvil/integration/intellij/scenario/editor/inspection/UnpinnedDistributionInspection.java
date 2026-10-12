package me.whereareiam.anvil.integration.intellij.scenario.editor.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Set;

import me.whereareiam.anvil.integration.intellij.scenario.editor.BuilderCalls;
import org.jetbrains.annotations.NotNull;

/**
 * Reports {@code Distribution.remote(version, "latest")} outside manual scenarios.
 * <p>
 * Automated runs refuse the latest build, because the same test would download a different server over time.
 * A scenario counts as manual when the same method calls {@code manual(true)} on its builder.
 */
public final class UnpinnedDistributionInspection extends AbstractBaseJavaLocalInspectionTool {
	private static final Set<String> FACTORY = Set.of(BuilderCalls.DISTRIBUTION);
	private static final Set<String> SCENARIO = Set.of(BuilderCalls.SCENARIO);

	@Override
	public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
		return new JavaElementVisitor() {
			@Override
			public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
				if (!BuilderCalls.calls(call, FACTORY, Set.of("remote"))) return;
				if (!"latest".equalsIgnoreCase(BuilderCalls.argument(call, 1)) || manual(call)) return;

				PsiExpression build = call.getArgumentList().getExpressions()[1];
				holder.registerProblem(build, "Automated runs refuse the latest build; pin a build, or mark the scenario manual");
			}
		};
	}

	private static boolean manual(@NotNull PsiMethodCallExpression call) {
		PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
		if (method == null) return false;

		for (PsiMethodCallExpression candidate : PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression.class)) {
			if (!BuilderCalls.calls(candidate, SCENARIO, Set.of("manual"))) continue;

			PsiExpression[] arguments = candidate.getArgumentList().getExpressions();
			if (arguments.length == 1 && arguments[0] instanceof PsiLiteralExpression literal && Boolean.TRUE.equals(literal.getValue()))
				return true;
		}

		return false;
	}
}
