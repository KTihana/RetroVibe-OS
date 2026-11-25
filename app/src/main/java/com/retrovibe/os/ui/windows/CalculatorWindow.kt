package com.retrovibe.os.ui.windows

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import kotlin.math.sqrt

@Composable
fun CalculatorWindow(vm: OSViewModel, palette: RetroPalette) {
    val clipboard = LocalClipboardManager.current
    var display by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var resetNext by remember { mutableStateOf(false) }
    var memory by remember { mutableStateOf(0.0) }
    var groupDigits by remember { mutableStateOf(true) }
    var copyBanner by remember { mutableStateOf(false) }

    fun click() = RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)

    fun formatNum(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "Error"
        val clean = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.8f".format(v).trimEnd('0').trimEnd('.')
        return clean
    }

    fun formattedDisplay(): String {
        if (display == "Error" || !groupDigits) return display
        val num = display.toDoubleOrNull() ?: return display
        return if (display.contains(".")) display else DecimalFormat("#,###").format(num)
    }

    fun inputDigit(d: String) {
        click()
        if (resetNext || display == "0" || display == "Error") {
            display = d
            resetNext = false
        } else if (display.length < 14) {
            display += d
        }
    }

    fun compute(a: Double, b: Double, op: String): Double = when (op) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    fun pressOp(op: String) {
        click()
        val cur = display.toDoubleOrNull() ?: 0.0
        if (acc != null && pendingOp != null && !resetNext) {
            val res = compute(acc!!, cur, pendingOp!!)
            display = formatNum(res)
            acc = res
        } else {
            acc = cur
        }
        pendingOp = op
        resetNext = true
    }

    fun pressEquals() {
        click()
        val a = acc ?: return
        val op = pendingOp ?: return
        val b = display.toDoubleOrNull() ?: 0.0
        val res = compute(a, b, op)
        display = formatNum(res)
        acc = null
        pendingOp = null
        resetNext = true
    }

    @Composable
    fun CalcKey(
        label: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val view = LocalView.current
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        var clickFlash by remember { mutableStateOf(false) }
        var clickSeq by remember { mutableIntStateOf(0) }
        LaunchedEffect(clickSeq) {
            if (clickSeq > 0) {
                clickFlash = true
                delay(120)
                clickFlash = false
            }
        }
        val isDown = pressed || clickFlash
        Box(
            modifier = modifier
                .height(30.dp)
                .win98Bevel(sunken = isDown, bg = if (isDown) Color(0xFFB5B0A6) else Color(0xFFD4D0C8))
                .clickable(interactionSource = interaction, indication = null) {
                    clickSeq++
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
                .offset(x = if (isDown) 1.5.dp else 0.dp, y = if (isDown) 1.5.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColor
            )
        }
    }

    val blue = Color(0xFF0000AA)
    val crimson = Color(0xFFAA0000)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD4D0C8))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Classic menu bar
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (copyBanner) "Copied!" else "Copy",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color.Black,
                modifier = Modifier.clickable {
                    click()
                    clipboard.setText(AnnotatedString(display))
                    copyBanner = true
                }
            )
            Text(
                text = if (groupDigits) "Commas: ON" else "Commas: OFF",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color.Black,
                modifier = Modifier.clickable {
                    click()
                    groupDigits = !groupDigits
                }
            )
        }

        // Double-bevel LCD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color.White)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = formattedDisplay(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Row 1: M indicator + Backspace / CE / C
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(30.dp)
                    .win98Bevel(sunken = true, bg = Color(0xFFD4D0C8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (memory != 0.0) "M" else "",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Black
                )
            }
            Spacer(Modifier.width(4.dp))
            CalcKey("Back", crimson, Modifier.weight(1f)) {
                click()
                display = if (display.length > 1) display.dropLast(1) else "0"
            }
            CalcKey("CE", crimson, Modifier.weight(1f)) {
                click()
                display = "0"
            }
            CalcKey("C", crimson, Modifier.weight(1f)) {
                click()
                display = "0"; acc = null; pendingOp = null; resetNext = false
            }
        }

        // Grid rows
        val rows = listOf(
            listOf("MC" to crimson, "7" to blue, "8" to blue, "9" to blue, "/" to crimson, "sqrt" to blue),
            listOf("MR" to crimson, "4" to blue, "5" to blue, "6" to blue, "*" to crimson, "%" to blue),
            listOf("MS" to crimson, "1" to blue, "2" to blue, "3" to blue, "-" to crimson, "1/x" to blue),
            listOf("M+" to crimson, "0" to blue, "+/-" to blue, "." to blue, "+" to crimson, "=" to crimson)
        )

        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                r.forEachIndexed { idx, (lbl, col) ->
                    if (idx == 1) Spacer(Modifier.width(4.dp))
                    CalcKey(
                        label = lbl,
                        textColor = col,
                        modifier = if (idx == 0) Modifier.width(34.dp) else Modifier.weight(1f)
                    ) {
                        when (lbl) {
                            in "0".."9" -> inputDigit(lbl)
                            "." -> {
                                click()
                                if (resetNext) { display = "0."; resetNext = false }
                                else if (!display.contains(".")) display += "."
                            }
                            "+", "-", "*", "/" -> pressOp(lbl)
                            "=" -> pressEquals()
                            "+/-" -> {
                                click()
                                val v = display.toDoubleOrNull() ?: 0.0
                                display = formatNum(-v)
                            }
                            "sqrt" -> {
                                click()
                                val v = display.toDoubleOrNull() ?: 0.0
                                display = if (v < 0) "Error" else formatNum(sqrt(v))
                                resetNext = true
                            }
                            "1/x" -> {
                                click()
                                val v = display.toDoubleOrNull() ?: 0.0
                                display = if (v == 0.0) "Error" else formatNum(1.0 / v)
                                resetNext = true
                            }
                            "%" -> {
                                click()
                                val v = display.toDoubleOrNull() ?: 0.0
                                val base = acc ?: 1.0
                                display = formatNum(base * v / 100.0)
                                resetNext = true
                            }
                            "MC" -> { click(); memory = 0.0 }
                            "MR" -> { click(); display = formatNum(memory); resetNext = true }
                            "MS" -> { click(); memory = display.toDoubleOrNull() ?: 0.0; resetNext = true }
                            "M+" -> { click(); memory += (display.toDoubleOrNull() ?: 0.0); resetNext = true }
                        }
                    }
                }
            }
        }
    }
}
