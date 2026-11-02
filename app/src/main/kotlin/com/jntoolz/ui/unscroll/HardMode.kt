package com.jntoolz.ui.unscroll

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntoolz.ui.sot.SotColors
import com.jntoolz.ui.sot.SotLabel
import com.jntoolz.ui.sot.SotText
import com.jntoolz.unscroll.Unlock

/** Shown before switching to Hard: what it locks and how to get out. */
@Composable
internal fun HardWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SotColors.Card,
        title = { SotText("HARD MODE", 16.sp, SotColors.Red) },
        text = {
            Column {
                for (line in listOf(
                    "Blocks and warnings lock on.",
                    "Soft or Off only after the unlock below.",
                    "To leave, type a sentence and a random code by hand.",
                    "No pasting. The code changes every time.",
                )) SotText("· $line", 13.sp, SotColors.Txt, bold = false, modifier = Modifier.padding(bottom = 8.dp))
            }
        },
        confirmButton = { DialogButton("GO HARD", SotColors.Red, onConfirm) },
        dismissButton = { DialogButton("CANCEL", SotColors.Dim, onDismiss) },
    )
}

/**
 * Leaving Hard: type [Unlock.target] exactly. Each change must be one typed character or a deletion; anything
 * else (paste, clipboard chip, autocomplete word) clears the field. The copy/paste menu is switched off.
 */
@Composable
internal fun UnlockDialog(onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sentence = remember { Unlock.sentence() }
    val code = remember { Unlock.code() }
    val target = remember(sentence, code) { Unlock.target(sentence, code) }
    var typed by remember { mutableStateOf("") }
    val done = Unlock.matches(typed, sentence, code)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SotColors.Card,
        title = { SotText("TYPE TO UNLOCK", 16.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SotLabel("TYPE THIS")
                    Text(
                        text = "${typed.length} / ${target.length}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (done) SotColors.Green else SotColors.Dim
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SotColors.Chip)
                        .border(1.dp, SotColors.Border, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = sentence,
                        color = SotColors.Txt,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = code,
                        color = SotColors.Red,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { new ->
                            if (Unlock.accepts(typed, new)) typed = new
                            else {
                                typed = ""
                                Toast.makeText(context, "Type it. No pasting.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Type to scroll",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = SotColors.Dim
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = SotColors.Txt,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        // Password keyboards drop suggestions, swipe typing and the clipboard strip.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (done) SotColors.Green else SotColors.Dim,
                            unfocusedBorderColor = SotColors.Border,
                            cursorColor = SotColors.Txt
                        )
                    )
                }
            }
        },
        confirmButton = {
            DialogButton("UNLOCK", if (done) SotColors.Green else SotColors.Faint) { if (done) onUnlocked() }
        },
        dismissButton = { DialogButton("UNSCROLL", SotColors.Dim, onDismiss) },
    )
}

@Composable
private fun DialogButton(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = color, fontFamily = FontFamily.Monospace) }
}

/** A text toolbar that never shows: no Copy / Paste / Select all in the unlock field. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {}
}
