package me.whereareiam.anvil.integration.intellij.view.window.main.environment.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.ValidationInfo;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JComponent;

import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.model.settings.CommandScope;
import me.whereareiam.anvil.integration.intellij.settings.ProjectCommandHistory;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionInput;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import org.jetbrains.annotations.Nullable;

/**
 * Native form generated from portable action inputs, retaining input until explicitly changed.
 */
final class ActionInvocationController {
	private final EnvironmentSession session;
	private final Consumer<Boolean> availability;
	private final Consumer<String> errors;
	private final BooleanSupplier disposed;
	private final ActionDescriptor action;
	private final ActionInvocationForm form;
	private boolean submitting;

	ActionInvocationController(
			EnvironmentSession session,
			ActionDescriptor action,
			ActionInvocationForm form,
			Consumer<Boolean> availability,
			Consumer<String> errors,
			BooleanSupplier disposed
	) {
		this.session = session;
		this.action = action;
		this.form = form;
		this.availability = availability;
		this.errors = errors;
		this.disposed = disposed;

		updateAvailability();
	}

	JComponent component() {
		return form.getComponent();
	}

	private CommandScope key(ActionInput input) {
		return CommandScope.builder()
				.source(session.getSource().getId())
				.definition(session.getScenario().getDefinition())
				.scenario(session.getScenario().getName())
				.target(action.getTarget().getType() + ":" + action.getTarget().getName())
				.operation(action.getDefinition().getId() + ":" + input.getName())
				.build();
	}

	@Nullable ValidationInfo validate() {
		return form.validateInputs();
	}

	void submit() {
		if (submitting || !canInvoke()) return;

		ValidationInfo validation = validate();
		if (validation != null) {
			errors.accept(validation.message);
			return;
		}

		Map<String, String> arguments = form.arguments();
		submitting = true;
		errors.accept(null);
		updateAvailability();
		session.invoke(action, arguments).whenComplete((result, failure) ->
				ApplicationManager.getApplication().invokeLater(
						() -> complete(arguments, result, failure), ModalityState.any()));
	}

	private void complete(Map<String, String> arguments, @Nullable ActionResult result, @Nullable Throwable failure) {
		if (disposed.getAsBoolean() || session.getIdeProject().isDisposed()) return;

		submitting = false;
		updateAvailability();
		if (failure != null) {
			errors.accept(failure.getMessage());
			return;
		}

		Objects.requireNonNull(result, "A successful invocation must return an action result");
		form.showResult(result);
		if (result.isSuccessful()) remember(arguments);

	}

	private void remember(Map<String, String> arguments) {
		for (ActionInput input : action.getDefinition().getInputs()) {
			if (input.isSensitive() || input.getType() == ActionInputType.BOOLEAN
					|| input.getType() == ActionInputType.CHOICE
					|| !arguments.containsKey(input.getName())) {
				continue;
			}

			session.getIdeProject().getService(ProjectCommandHistory.class)
					.submitted(key(input), arguments.get(input.getName()));
		}
	}

	private boolean canInvoke() {
		return session.isActive()
				&& session.getSnapshot().getSessionId() != null
				&& session.getSnapshot().getState() == SessionState.RUNNING
				&& session.getSnapshot().getActions()
					.stream()
					.anyMatch(current ->
							current.getTarget().equals(action.getTarget())
									&& current.getDefinition().getId().equals(action.getDefinition().getId())
									&& current.getAvailability().isEnabled());
	}

	private void updateAvailability() {
		availability.accept(!submitting && canInvoke());
	}

	void close() {
		session.actionDraft(action, form.draft());
	}
}
