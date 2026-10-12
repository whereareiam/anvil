package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import me.whereareiam.anvil.integration.intellij.scenario.window.ScenarioPresentation;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Links process names in a scenario's Java source to the servers and proxies declared in the same file.
 * <p>
 * The string passed to {@code entrypoint}, or to a proxy's {@code server} and {@code defaultServer}, completes the
 * names given to {@code MinecraftServer} and {@code MinecraftProxy} builders, and navigates to that declaration.
 * The references are soft: a process declared in another file, or named at run time, is not reported.
 */
public final class ProcessNameReferences extends PsiReferenceContributor {
	private static final Set<String> SCENARIO = Set.of(BuilderCalls.SCENARIO);
	private static final Set<String> PROXY = Set.of(BuilderCalls.PROXY);
	private static final Set<String> PROCESSES = Set.of(BuilderCalls.SERVER, BuilderCalls.PROXY);

	@Override
	public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
		registrar.registerReferenceProvider(PsiJavaPatterns.literalExpression(), new PsiReferenceProvider() {
			@Override
			public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
				if (!(element instanceof PsiLiteralExpression literal) || !(literal.getValue() instanceof String)) return PsiReference.EMPTY_ARRAY;

				PsiMethodCallExpression call = BuilderCalls.argumentOf(literal);
				if (call == null || !namesProcess(call)) return PsiReference.EMPTY_ARRAY;

				return new PsiReference[] {new ProcessReference(literal)};
			}
		});
	}

	private static boolean namesProcess(@NotNull PsiMethodCallExpression call) {
		return BuilderCalls.calls(call, SCENARIO, Set.of("entrypoint")) || BuilderCalls.calls(call, PROXY, Set.of("server", "defaultServer"));
	}

	/**
	 * Returns the processes the file declares by name, mapped to the expression that names each one.
	 */
	private static @NotNull Map<String, Declaration> declarations(@NotNull PsiFile file) {
		return CachedValuesManager.getCachedValue(file, () -> {
			Map<String, Declaration> declarations = new LinkedHashMap<>();
			for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression.class)) {
				if (!BuilderCalls.calls(call, PROCESSES, Set.of("name"))) continue;

				String name = BuilderCalls.argument(call, 0);
				if (name == null) continue;

				PsiExpression declaration = call.getArgumentList().getExpressions()[0];
				ProcessRole role = BuilderCalls.calls(call, PROXY, Set.of("name")) ? ProcessRole.PROXY : ProcessRole.SERVER;
				declarations.putIfAbsent(name, new Declaration(declaration, role));
			}

			return CachedValueProvider.Result.create(declarations, PsiModificationTracker.MODIFICATION_COUNT);
		});
	}

	private record Declaration(@NotNull PsiExpression name, @NotNull ProcessRole role) {}

	private static final class ProcessReference extends PsiReferenceBase<PsiLiteralExpression> {
		private ProcessReference(@NotNull PsiLiteralExpression literal) {
			super(literal, TextRange.from(1, Math.max(0, literal.getTextLength() - 2)), true);
		}

		@Override
		public @Nullable PsiElement resolve() {
			Declaration declaration = declarations(getElement().getContainingFile()).get(getValue());
			return declaration == null ? null : declaration.name();
		}

		@Override
		public Object @NotNull [] getVariants() {
			return declarations(getElement().getContainingFile()).entrySet().stream()
					.map(entry -> LookupElementBuilder.create(entry.getKey())
							.withIcon(ScenarioPresentation.processIcon(entry.getValue().role()))
							.withTypeText(entry.getValue().role() == ProcessRole.PROXY ? "proxy" : "server"))
					.toArray();
		}
	}
}
