package me.whereareiam.anvil.protocol.api.model.text.hover;

/**
 * What the game shows while the pointer is over text.
 */
public sealed interface TextHover permits ShowTextHover, ShowItemHover, ShowEntityHover {
}
