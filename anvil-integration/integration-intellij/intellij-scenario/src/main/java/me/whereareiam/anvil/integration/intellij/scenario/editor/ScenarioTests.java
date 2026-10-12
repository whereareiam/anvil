package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the JUnit tests in project sources that start a given scenario through {@value #ANNOTATION}.
 * <p>
 * A test class annotated with the scenario counts as a whole; a method counts on its own only when its class does
 * not already name that scenario. Only the annotation's class literal is read, so tests that pick their scenario
 * another way, such as through a custom environment annotation, are not found.
 */
@UtilityClass
public class ScenarioTests {
	/**
	 * The JUnit annotation that names the scenario a test runs in.
	 */
	public final String ANNOTATION = "me.whereareiam.anvil.integration.junit.AnvilTest";

	/**
	 * Returns the test classes and methods that run in the definition's scenario, ordered by name.
	 *
	 * @param definition scenario definition class
	 * @return the tests, or an empty list when none name the definition or JUnit integration is not on the classpath
	 */
	public @NotNull List<PsiModifierListOwner> using(@NotNull PsiClass definition) {
		Project project = definition.getProject();
		String name = definition.getQualifiedName();
		PsiClass annotation = JavaPsiFacade.getInstance(project).findClass(ANNOTATION, GlobalSearchScope.allScope(project));
		if (name == null || annotation == null) return List.of();

		List<PsiModifierListOwner> tests = new ArrayList<>();
		var search = AnnotatedElementsSearch.searchElements(
				annotation, GlobalSearchScope.projectScope(project), PsiClass.class, PsiMethod.class);
		for (PsiModifierListOwner test : search.findAll()) {
			if (!names(test, name) || !runnable(test)) continue;
			if (test instanceof PsiMethod method && names(method.getContainingClass(), name)) continue;
			tests.add(test);
		}
		tests.sort(Comparator.comparing(ScenarioTests::label));

		return tests;
	}

	/**
	 * Names a test as the run configuration would: {@code Class} or {@code Class.method}.
	 */
	public @NotNull String label(@NotNull PsiModifierListOwner test) {
		if (test instanceof PsiMethod method && method.getContainingClass() != null)
			return method.getContainingClass().getName() + "." + method.getName();

		return test instanceof PsiClass type && type.getName() != null ? type.getName() : String.valueOf(test);
	}

	private boolean names(@Nullable PsiModifierListOwner test, @NotNull String definition) {
		PsiAnnotation annotation = test == null ? null : test.getAnnotation(ANNOTATION);
		if (annotation == null) return false;

		return annotation.findDeclaredAttributeValue("value") instanceof PsiClassObjectAccessExpression literal
				&& definition.equals(literal.getOperand().getType().getCanonicalText());
	}

	private boolean runnable(@NotNull PsiModifierListOwner test) {
		if (test instanceof PsiClass type) return !type.isInterface() && !type.hasModifierProperty(PsiModifier.ABSTRACT);

		return !test.hasModifierProperty(PsiModifier.ABSTRACT);
	}
}
