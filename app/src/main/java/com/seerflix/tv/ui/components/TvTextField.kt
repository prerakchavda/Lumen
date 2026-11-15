package com.seerflix.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/**
 * TV-friendly wrapper for a text field.
 *
 * On Android TV a plain TextField grabs input the moment the D-pad lands on it: the on-screen
 * keyboard pops up and swallows the remote, so just *moving past* a form opens the keyboard on
 * every field. Here the D-pad focuses a lightweight container instead (drawn with a focus ring);
 * pressing OK/Enter hands focus to the real field and shows the keyboard. Leaving the field
 * (IME Next/Done, D-pad out) returns to the non-editing state.
 *
 * Usage:
 * ```
 * TvTextFieldContainer { fieldModifier ->
 *     OutlinedTextField(..., modifier = fieldModifier.fillMaxWidth())
 * }
 * ```
 *
 * @param startEditing when it turns true (e.g. an empty search screen opening), the field is
 *   activated immediately with the keyboard shown.
 * @param focusRingColor colour of the ring drawn while the container (not the field) is focused.
 */
@Composable
fun TvTextFieldContainer(
    modifier: Modifier = Modifier,
    startEditing: Boolean = false,
    focusRingColor: Color = Color.White,
    containerFocusRequester: FocusRequester? = null,
    content: @Composable (fieldModifier: Modifier) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var containerFocused by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(startEditing) {
        if (startEditing) editing = true
    }
    LaunchedEffect(editing) {
        if (editing) {
            // Let focusProperties(canFocus = true) apply before moving focus into the field.
            withFrameNanos { }
            runCatching { fieldRequester.requestFocus() }
            keyboard?.show()
        }
    }

    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .then(if (containerFocusRequester != null) Modifier.focusRequester(containerFocusRequester) else Modifier)
            .onFocusChanged { containerFocused = it.isFocused }
            .then(
                if (containerFocused && !editing) Modifier.border(2.dp, focusRingColor, shape) else Modifier
            )
            // clickable = focusable + DPAD_CENTER/Enter (and touch) activation.
            .clickable(interactionSource = interaction, indication = null) { editing = true }
    ) {
        content(
            Modifier
                .focusRequester(fieldRequester)
                .focusProperties { canFocus = editing }
                .onFocusChanged { state ->
                    if (!state.isFocused && editing) editing = false
                }
                // A single-line TextField eats D-pad Up/Down (cursor to start/end), so after typing
                // and closing the keyboard the remote was stuck in the field. While the keyboard is
                // up these keys go to the IME window instead, so this only fires once it's closed.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                }
        )
    }
}
