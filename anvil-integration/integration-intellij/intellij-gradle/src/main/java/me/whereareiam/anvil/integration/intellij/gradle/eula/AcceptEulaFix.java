package me.whereareiam.anvil.integration.intellij.gradle.eula;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Adds {@code acceptEula()} to the script's {@code anvil} block, or appends that block when the script has none.
 */
final class AcceptEulaFix implements LocalQuickFix {
	private static final Pattern BLOCK = Pattern.compile("(?m)^([ \\t]*)anvil\\s*\\{[ \\t]*");

	@Override
	public @NotNull String getFamilyName() {
		return "Accept the Minecraft EULA";
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
		PsiFile file = descriptor.getPsiElement().getContainingFile();
		Document document = file == null ? null : PsiDocumentManager.getInstance(project).getDocument(file);
		if (document == null) return;

		Insertion insertion = insertion(document.getText());
		document.insertString(insertion.offset(), insertion.text());
		PsiDocumentManager.getInstance(project).commitDocument(document);
	}

	@Override
	public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
		PsiFile file = previewDescriptor.getPsiElement().getContainingFile();
		if (file == null) return IntentionPreviewInfo.EMPTY;

		String original = file.getText();
		Insertion insertion = insertion(original);
		String modified = original.substring(0, insertion.offset()) + insertion.text() + original.substring(insertion.offset());

		return new IntentionPreviewInfo.CustomDiff(file.getFileType(), file.getName(), original, modified);
	}

	/**
	 * Returns where and what to insert into a script so that it accepts the EULA.
	 */
	static @NotNull Insertion insertion(@NotNull String script) {
		String indent = script.contains("\n\t") ? "\t" : "    ";
		Matcher block = BLOCK.matcher(script);
		if (block.find()) return new Insertion(block.end(), "\n" + block.group(1) + indent + "acceptEula()");

		String separator = script.isEmpty() || script.endsWith("\n") ? "\n" : "\n\n";
		return new Insertion(script.length(), separator + "anvil {\n" + indent + "acceptEula()\n}\n");
	}

	record Insertion(int offset, @NotNull String text) {}
}
