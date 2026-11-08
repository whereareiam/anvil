package me.whereareiam.anvil.protocol.api.model.text.content;

/**
 * What one text component contributes before its children: text as written, a translation, a keybind, a
 * selector, a score, NBT, a sprite or a player head. A component of each kind is drawn as the game draws it; see
 * {@link me.whereareiam.anvil.protocol.api.model.text.Text#spans()} for the text each kind contributes.
 */
public sealed interface TextContent permits LiteralContent, TranslatedContent, KeybindContent, SelectorContent, ScoreContent, NbtContent, SpriteContent, PlayerHeadContent {
}
