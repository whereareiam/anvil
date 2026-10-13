package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.InheritanceUtil;

import java.nio.file.Path;

import lombok.experimental.UtilityClass;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

/**
 * Recognizes scenario definition classes in source code, in any language with UAST support.
 * <p>
 * A definition is a concrete class in project sources that implements {@value #DEFINITION}; each one declares
 * exactly one scenario, so the class name identifies the scenario.
 */
@UtilityClass
public class ScenarioDefinitions {
	/**
	 * The interface every scenario definition implements.
	 */
	public final String DEFINITION = "me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition";

	/**
	 * Returns the definition class whose name identifier is the element, as gutter icons are placed.
	 */
	public @Nullable PsiClass atIdentifier(@NotNull PsiElement element) {
		UElement parent = UastUtils.getUParentForIdentifier(element);
		if (!(parent instanceof UClass declaration)) return null;

		return definition(declaration.getJavaPsi());
	}

	/**
	 * Returns the definition class that contains the element, such as a caret position in its source.
	 */
	public @Nullable PsiClass containing(@Nullable PsiElement element) {
		if (element == null) return null;

		UClass declaration = UastContextKt.getUastParentOfType(element, UClass.class, false);
		while (declaration != null) {
			PsiClass definition = definition(declaration.getJavaPsi());
			if (definition != null) return definition;
			declaration = UastContextKt.getUastParentOfType(declaration.getSourcePsi(), UClass.class, true);
		}

		return null;
	}

	/**
	 * Returns the scenario source whose module contains the class, or null when the class is not in one.
	 */
	public @Nullable ScenarioSource source(@NotNull PsiClass definition) {
		VirtualFile file = definition.getContainingFile() == null ? null : definition.getContainingFile().getVirtualFile();
		if (file == null) return null;

		return definition.getProject().getService(BuildIntegrations.class).findSource(Path.of(file.getPath()));
	}

	private @Nullable PsiClass definition(@Nullable PsiClass candidate) {
		if (candidate == null || candidate.isInterface() || candidate.isEnum() || candidate.isAnnotationType()) return null;
		if (candidate.hasModifierProperty(PsiModifier.ABSTRACT) || candidate.getQualifiedName() == null) return null;
		if (!inSources(candidate)) return null;

		return InheritanceUtil.isInheritor(candidate, DEFINITION) ? candidate : null;
	}

	private boolean inSources(@NotNull PsiClass candidate) {
		Project project = candidate.getProject();
		VirtualFile file = candidate.getContainingFile() == null ? null : candidate.getContainingFile().getVirtualFile();

		return file != null && ProjectFileIndex.getInstance(project).isInSourceContent(file);
	}
}
