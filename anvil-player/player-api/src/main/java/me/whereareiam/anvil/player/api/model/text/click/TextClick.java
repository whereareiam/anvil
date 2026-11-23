package me.whereareiam.anvil.player.api.model.text.click;

/**
 * What a click on text does.
 */
public sealed interface TextClick
		permits OpenUrlClick, OpenFileClick, RunCommandClick, SuggestCommandClick, ChangePageClick, CopyToClipboardClick, ShowDialogClick,
		CustomClick {
}
