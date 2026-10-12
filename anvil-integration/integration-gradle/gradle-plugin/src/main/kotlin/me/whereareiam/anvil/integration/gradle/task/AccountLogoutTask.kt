package me.whereareiam.anvil.integration.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Removes the account selected with `--account=<id>` from the account store.
 */
@UntrackedTask(because = "Removing an account changes the local account store outside the build")
abstract class AccountLogoutTask : AccountTask() {
    /**
     * Removes the selected account.
     */
    @TaskAction
    fun logout() {
        authenticate(listOf("--logout"))
    }
}
