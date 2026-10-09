package me.whereareiam.anvil.integration.intellij.view.window.account.pool

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary
import me.whereareiam.anvil.integration.intellij.model.account.AvailableAccount
import org.jetbrains.annotations.Nullable
import java.io.IOException
import javax.swing.JComponent

/**
 * Creates or edits one account pool; validation and saving belong to [PoolEditorController].
 */
class PoolEditorDialog(
	project: Project,
	library: AccountLibrary,
	@Nullable original: String?,
	pools: Map<String, List<String>>,
	accounts: List<AvailableAccount>
) : DialogWrapper(project, false) {
	private val controller = PoolEditorController(library, original, pools, accounts)

	init {
		title = if (original == null) "Create AAccount Pool" else "Edit Account Pool"
		setOKButtonText("Save Pool")
		init()
	}

	override fun createCenterPanel(): JComponent = controller.form().component
	override fun doValidate(): ValidationInfo? = controller.validate()
	override fun doOKAction() {
		val problem = doValidate()
		if (problem != null) {
			setErrorText(problem.message)
			return
		}
		try {
			controller.save()
			super.doOKAction()
		} catch (failure: IOException) {
			setErrorText(failure.message)
		}
	}
}
