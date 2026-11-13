package com.seerflix.tv.core.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AssStyleSpec(
    val name: String,
    val alignment: Int = 2,
    val primaryColor: Color = Color.White,
    val bold: Boolean = false,
    val italic: Boolean = false
)

data class AssCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val alignment: Int = 2,
    val primaryColor: Color = Color.White,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

object AssSubtitleParser {
    private val alignmentRegex = Regex("""\\an([1-9])""")
    private val colorRegex = Regex("""\\(?:1?c)&H([0-9A-Fa-f]{2,8})&?""")
    private val boldRegex = Regex("""\\b([01])""")
    private val italicRegex = Regex("""\\i([01])""")
    private val tagBlockRegex = Regex("""\{([^}]*)\}""")

    fun parse(rawContent: String): List<AssCue> {
        if (rawContent.isBlank()) return emptyList()
        val styles = mutableMapOf<String, AssStyleSpec>()
        val cues = mutableListOf<AssCue>()

        var section = ""
        var styleFormat = emptyList<String>()
        var eventFormat = emptyList<String>()

        rawContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";")) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.lowercase()
                return@forEach
            }

            when (section) {
                "[v4+ styles]", "[v4 styles]" -> {
                    when {
                        line.startsWith("Format:", ignoreCase = true) -> {
                            styleFormat = line.substringAfter(":")
                                .split(",")
                                .map { it.trim().lowercase() }
                        }
                        line.startsWith("Style:", ignoreCase = true) -> {
                            val values = line.substringAfter(":").split(",").map { it.trim() }
                            val nameIdx = styleFormat.indexOf("name").takeIf { it >= 0 } ?: 0
                            val name = values.getOrNull(nameIdx)?.takeIf { it.isNotBlank() } ?: return@forEach
                            val alignIdx = styleFormat.indexOf("alignment")
                            val colorIdx = styleFormat.indexOf("primarycolour")
                            val boldIdx = styleFormat.indexOf("bold")
                            val italicIdx = styleFormat.indexOf("italic")

                            styles[name] = AssStyleSpec(
                                name = name,
                                alignment = alignIdx.takeIf { it >= 0 }
                                    ?.let { values.getOrNull(it)?.toIntOrNull() }
                                    ?: 2,
                                primaryColor = colorIdx.takeIf { it >= 0 }
                                    ?.let { values.getOrNull(it)?.let(::parseAssColor) }
                                    ?: Color.White,
                                bold = boldIdx.takeIf { it >= 0 }
                                    ?.let { values.getOrNull(it) == "-1" || values.getOrNull(it) == "1" }
                                    ?: false,
                                italic = italicIdx.takeIf { it >= 0 }
                                    ?.let { values.getOrNull(it) == "-1" || values.getOrNull(it) == "1" }
                                    ?: false
                            )
                        }
                    }
                }
                "[events]" -> {
                    when {
                        line.startsWith("Format:", ignoreCase = true) -> {
                            eventFormat = line.substringAfter(":")
                                .split(",")
                                .map { it.trim().lowercase() }
                        }
                        line.startsWith("Dialogue:", ignoreCase = true) -> {
                            val fieldCount = if (eventFormat.isNotEmpty()) eventFormat.size else 10
                            val values = line.substringAfter(":").trimStart().split(",", limit = fieldCount)
                            val startIdx = eventFormat.indexOf("start").takeIf { it >= 0 } ?: 1
                            val endIdx = eventFormat.indexOf("end").takeIf { it >= 0 } ?: 2
                            val styleIdx = eventFormat.indexOf("style").takeIf { it >= 0 } ?: 3
                            val textIdx = eventFormat.indexOf("text").takeIf { it >= 0 } ?: (fieldCount - 1)

                            val startMs = parseTimestampMs(values.getOrNull(startIdx).orEmpty()) ?: return@forEach
                            val endMs = parseTimestampMs(values.getOrNull(endIdx).orEmpty()) ?: return@forEach
                            if (endMs <= startMs) return@forEach

                            val styleName = values.getOrNull(styleIdx)?.trim().orEmpty()
                            val styleSpec = styles[styleName] ?: AssStyleSpec(name = styleName)
                            val rawText = values.getOrNull(textIdx).orEmpty()

                            var alignment = styleSpec.alignment
                            var color = styleSpec.primaryColor
                            var isBold = styleSpec.bold
                            var isItalic = styleSpec.italic

                            tagBlockRegex.findAll(rawText).forEach { match ->
                                val block = match.groupValues[1]
                                alignmentRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                                    alignment = it
                                }
                                colorRegex.find(block)?.groupValues?.getOrNull(1)?.let { hex ->
                                    parseAssColor("&H$hex&")?.let { parsed -> color = parsed }
                                }
                                boldRegex.find(block)?.groupValues?.getOrNull(1)?.let {
                                    isBold = it == "1"
                                }
                                italicRegex.find(block)?.groupValues?.getOrNull(1)?.let {
                                    isItalic = it == "1"
                                }
                            }

                            val cleanText = rawText
                                .replace(tagBlockRegex, "")
                                .replace("\\N", "\n")
                                .replace("\\n", "\n")
                                .replace("\\h", " ")
                                .trim()

                            if (cleanText.isNotEmpty()) {
                                cues += AssCue(
                                    startMs = startMs,
                                    endMs = endMs,
                                    text = cleanText,
                                    alignment = alignment.coerceIn(1, 9),
                                    primaryColor = color,
                                    isBold = isBold,
                                    isItalic = isItalic
                                )
                            }
                        }
                    }
                }
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun parseTimestampMs(raw: String): Long? {
        val trimmed = raw.trim()
        val parts = trimmed.split(":")
        if (parts.size != 3) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val secParts = parts[2].split(".")
        val seconds = secParts.getOrNull(0)?.toLongOrNull() ?: return null
        val centisRaw = secParts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
        val centis = centisRaw.toLongOrNull() ?: 0L
        return (hours * 3600L + minutes * 60L + seconds) * 1000L + centis * 10L
    }

    fun parseAssColor(raw: String): Color? {
        val cleaned = raw.trim()
            .removePrefix("&H")
            .removePrefix("&h")
            .removeSuffix("&")
            .padStart(6, '0')
        val value = cleaned.toLongOrNull(16) ?: return null
        // ASS colors are &HAABBGGRR (00 alpha = opaque, FF alpha = transparent)
        val b = ((value shr 16) and 0xFF).toInt()
        val g = ((value shr 8) and 0xFF).toInt()
        val r = (value and 0xFF).toInt()
        val aByte = if (cleaned.length >= 8) ((value shr 24) and 0xFF).toInt() else 0
        val alpha = 255 - aByte
        return Color(red = r, green = g, blue = b, alpha = alpha.coerceIn(0, 255))
    }
}

@Composable
fun AssSubtitleOverlay(
    cues: List<AssCue>,
    positionMs: Long,
    fontScale: Float,
    backgroundAlpha: Float,
    modifier: Modifier = Modifier
) {
    if (cues.isEmpty()) return
    val activeCues = cues.filter { positionMs in it.startMs..it.endMs }
    if (activeCues.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp)
    ) {
        activeCues.forEach { cue ->
            AssCueText(
                cue = cue,
                fontScale = fontScale,
                backgroundAlpha = backgroundAlpha
            )
        }
    }
}

@Composable
private fun BoxScope.AssCueText(
    cue: AssCue,
    fontScale: Float,
    backgroundAlpha: Float
) {
    val alignment = when (cue.alignment) {
        1 -> Alignment.BottomStart
        2 -> Alignment.BottomCenter
        3 -> Alignment.BottomEnd
        4 -> Alignment.CenterStart
        5 -> Alignment.Center
        6 -> Alignment.CenterEnd
        7 -> Alignment.TopStart
        8 -> Alignment.TopCenter
        9 -> Alignment.TopEnd
        else -> Alignment.BottomCenter
    }
    val textAlign = when (cue.alignment) {
        1, 4, 7 -> TextAlign.Start
        3, 6, 9 -> TextAlign.End
        else -> TextAlign.Center
    }

    Box(
        modifier = Modifier
            .align(alignment)
            .background(
                color = Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f)),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = cue.text,
            color = cue.primaryColor,
            textAlign = textAlign,
            fontSize = (22f * fontScale.coerceIn(0.6f, 2.0f)).sp,
            fontWeight = if (cue.isBold) FontWeight.Bold else FontWeight.SemiBold,
            fontStyle = if (cue.isItalic) FontStyle.Italic else FontStyle.Normal,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.9f),
                    offset = Offset(2f, 2f),
                    blurRadius = 4f
                )
            )
        )
    }
}
