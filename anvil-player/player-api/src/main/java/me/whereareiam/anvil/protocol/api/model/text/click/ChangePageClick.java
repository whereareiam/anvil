package me.whereareiam.anvil.protocol.api.model.text.click;

import lombok.Value;

/**
 * Turns a book to a page.
 */
@Value
public class ChangePageClick implements TextClick {
	/**
	 * The page, counted from one.
	 */
	int page;
}
