package me.whereareiam.anvil.integration.intellij.account.view.pool

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel as formPanel
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.account.AvailableAccount
import me.whereareiam.anvil.integration.intellij.account.view.accessibleAccountName
import me.whereareiam.anvil.integration.intellij.account.view.availableEditions
import me.whereareiam.anvil.integration.intellij.account.view.profileNames
import me.whereareiam.anvil.integration.intellij.account.view.withEditionChips

internal class PoolEditorForm(
	original: String?,
	selected: List<String>,
	accounts: List<AvailableAccount>
) {
	private val members = linkedMapOf<String, JBCheckBox>()
	private val name = JBTextField(original.orEmpty()).apply {
		accessibleContext.accessibleName = "Pool name"
	}

	val component = formPanel {
		row("Pool name:") { cell(name).align(AlignX.FILL) }
		row { label("Select the accounts in this pool:") }
		val choices = formPanel {
			for (entry in accounts) {
				val account = entry.account
				val id = account.accountId
				if (members.containsKey(id)) continue
				val username = entry.profileNames()?.let { " · $it" }.orEmpty()
				val label = "$id · ${entry.source.label}$username"
				val editions = entry.availableEditions()
				val member = JBCheckBox(label, id in selected).apply {
					accessibleContext.accessibleName = accessibleAccountName(label, editions)
				}
				members[id] = member
				row { cell(withEditionChips(member, editions)) }
			}
			for (id in selected) {
				if (members.containsKey(id)) continue
				row {
					members[id] = checkBox("$id · Not currently available")
						.applyToComponent { isSelected = true }.component
				}
			}
		}
		row { scrollCell(choices).align(Align.FILL) }.resizableRow()
	}.apply { preferredSize = JBUI.size(460, 280) }

	fun poolName(): String = name.text.trim()

	fun selectedMembers(): List<String> = members.filterValues { it.isSelected }.keys.toList()

	fun validateName(original: String?, existing: Set<String>): ValidationInfo? {
		val id = poolName()
		if (id.isEmpty()) return ValidationInfo("Enter a pool name.", name)
		if (id != original && id in existing) return ValidationInfo("A pool with this name already exists.", name)
		if (selectedMembers().isEmpty()) return ValidationInfo("Select at least one account.")
		return null
	}
}
