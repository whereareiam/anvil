package me.whereareiam.anvil.integration.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Signs the account selected with `--account=<id>` in and stores it.
 */
@UntrackedTask(because = "Interactive sign-in changes the local account store outside the build")
abstract class AccountLoginTask : AccountTask() {
    /**
     * Signs the selected account in.
     */
    @TaskAction
    fun login() {
        authenticate(emptyList())
    }
}
