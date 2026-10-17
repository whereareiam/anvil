package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads calls on the scenario API's builders and factories in Java sources, such as
 * {@code MinecraftServer.builder().platform(Platforms.PAPER).distribution(Distribution.remote("1.21.11", "132"))}.
 * <p>
 * Only values the compiler can evaluate are read: literals and constants. Anything computed at run time, such as a
 * helper method's parameter, is left to the engine's own validation.
 */
@UtilityClass
public class BuilderCalls {
	/**
	 * The scenario builder, which names the entrypoint and holds the processes.
	 */
	public final String SCENARIO = "me.whereareiam.anvil.api.model.scenario.AnvilScenario.AnvilScenarioBuilder";

	/**
	 * The server builder.
	 */
	public final String SERVER = "me.whereareiam.anvil.api.model.process.MinecraftServer.MinecraftServerBuilder";

	/**
	 * The proxy builder, which also names the servers it routes to.
	 */
	public final String PROXY = "me.whereareiam.anvil.api.model.process.MinecraftProxy.MinecraftProxyBuilder";

	/**
	 * The distribution factories.
	 */
	public final String DISTRIBUTION = "me.whereareiam.anvil.api.model.process.Distribution";

	/**
	 * Returns whether the call invokes one of the named methods declared by one of the classes.
	 */
	public boolean calls(@NotNull PsiMethodCallExpression call, @NotNull Set<String> classes, @NotNull Set<String> methods) {
		if (!methods.contains(call.getMethodExpression().getReferenceName())) return false;

		PsiMethod method = call.resolveMethod();
		PsiClass owner = method == null ? null : method.getContainingClass();

		return owner != null && classes.contains(owner.getQualifiedName());
	}

	/**
	 * Returns the compile-time string value of an argument, or null when it has none.
	 */
	public @Nullable String argument(@NotNull PsiMethodCallExpression call, int index) {
		PsiExpression[] arguments = call.getArgumentList().getExpressions();
		if (index >= arguments.length) return null;

		return JavaConstantExpressionEvaluator.computeConstantExpression(arguments[index], false) instanceof String value ? value : null;
	}

	/**
	 * Returns the call whose argument list holds the element, or null when it is not an argument.
	 */
	public @Nullable PsiMethodCallExpression argumentOf(@NotNull PsiElement argument) {
		PsiElement parent = PsiUtil.skipParenthesizedExprUp(argument.getParent());
		return parent instanceof PsiExpressionList list && list.getParent() instanceof PsiMethodCallExpression call ? call : null;
	}

	/**
	 * Returns every call of the fluent chain the call belongs to, from the first to the last.
	 */
	public @NotNull List<PsiMethodCallExpression> chain(@NotNull PsiMethodCallExpression call) {
		PsiMethodCallExpression last = call;
		while (last.getParent() instanceof PsiReferenceExpression reference
				&& reference.getParent() instanceof PsiMethodCallExpression outer)
			last = outer;

		List<PsiMethodCallExpression> calls = new ArrayList<>();
		for (PsiExpression current = last; current instanceof PsiMethodCallExpression next; ) {
			calls.addFirst(next);
			current = next.getMethodExpression().getQualifierExpression();
		}

		return calls;
	}
}
