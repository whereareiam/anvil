package me.whereareiam.anvil.integration.intellij.view.window.account;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import me.whereareiam.anvil.integration.intellij.account.authentication.AccountEnrollment;
import org.jetbrains.annotations.NotNull;

final class AuthenticationController {
	private static final Pattern OPEN_LINK = Pattern.compile("^Open (https?://\\S+)$");
	private final @NotNull Project project;
	private final @NotNull AuthenticationForm form;
	private final @NotNull Runnable changed;
	private final @NotNull Runnable completed;

	AuthenticationController(
			@NotNull Project project,
			@NotNull String accountId,
			@NotNull Function<Consumer<String>, ? extends AccountEnrollment> factory,
			@NotNull AuthenticationForm form,
			@NotNull Runnable changed,
			@NotNull Runnable completed
	) {
		this.project = project;
		this.form = form;
		this.changed = changed;
		this.completed = completed;

		AccountEnrollment authentication = factory.apply(this::append);
		Disposer.register(form.disposable(), authentication);
		authentication.start(command -> ApplicationManager.getApplication().executeOnPooledThread(command))
				.whenComplete((ignored, failure) -> ApplicationManager.getApplication().invokeLater(
						() -> complete(failure), ModalityState.any()));
	}

	void complete(Throwable failure) {
		if (project.isDisposed()) return;

		changed.run();
		if (form.disposed()) return;
		if (failure == null) {
			form.complete("Account added to this project.");
			completed.run();
			return;
		}

		if (failure instanceof CancellationException) {
			form.complete("Sign-in cancelled.");
			completed.run();
			return;
		}

		form.complete("Could not complete sign-in.");
		completed.run();
		append(failure.getMessage());
	}

	private void append(String line) {
		ApplicationManager.getApplication().invokeLater(() -> {
			if (form.disposed() || project.isDisposed()) return;

			form.append(line);
			var match = OPEN_LINK.matcher(line);
			if (!match.matches()) return;

			URI uri;
			try {
				uri = URI.create(match.group(1));
			} catch (IllegalArgumentException invalid) {
				return;
			}

			form.showSignIn(
					() -> BrowserUtil.browse(uri),
					() -> CopyPasteManager.getInstance().setContents(new StringSelection(uri.toString()))
			);

		}, ModalityState.any());
	}
}
