package me.whereareiam.anvil.integration.intellij.scenario;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

/**
 * Balloon notifications of the scenario feature, in the IDE's "Anvil" notification group.
 */
@UtilityClass
public class ScenarioNotifications {
	private final String GROUP = "Anvil";

	/**
	 * Reports a failed request, such as a scenario that could not start.
	 */
	public void error(
			@NotNull Project project,
			@NotNull String title,
			@NotNull String content,
			@NotNull NotificationAction... actions
	) {
		show(project, title, content, NotificationType.ERROR, actions);
	}

	/**
	 * Confirms a completed request whose result is not visible where the user acted.
	 */
	public void info(
			@NotNull Project project,
			@NotNull String title,
			@NotNull String content,
			@NotNull NotificationAction... actions
	) {
		show(project, title, content, NotificationType.INFORMATION, actions);
	}

	/**
	 * Creates a notification link that runs a command and then closes the notification.
	 */
	public @NotNull NotificationAction action(@NotNull String text, @NotNull Runnable command) {
		return NotificationAction.createSimpleExpiring(text, command);
	}

	private void show(
			@NotNull Project project,
			@NotNull String title,
			@NotNull String content,
			@NotNull NotificationType type,
			@NotNull NotificationAction... actions
	) {
		Notification notification = NotificationGroupManager.getInstance()
				.getNotificationGroup(GROUP)
				.createNotification(title, escape(content), type);
		for (NotificationAction action : actions) notification.addAction(action);
		notification.notify(project);
	}

	private @NotNull String escape(@NotNull String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
