package com.example.suraksha.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suraksha.MainActivity

class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorScreen(
                onSecretUnlocked = {
                    val intent = Intent(this@CalculatorActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}

// ── Colour palette (stock Android calculator aesthetic) ──────────────────
private val BackgroundColor = Color(0xFF1C1C1E)
private val DisplayBackground = Color(0xFF1C1C1E)
private val DigitButtonColor = Color(0xFF505050)
private val OperatorButtonColor = Color(0xFFFF9500)
private val FunctionButtonColor = Color(0xFF333333)
private val DigitTextColor = Color.White
private val OperatorTextColor = Color.White
private val FunctionTextColor = Color.White
private val DisplayTextColor = Color.White

@Composable
private fun CalculatorScreen(onSecretUnlocked: () -> Unit) {

    // ── Calculator state ─────────────────────────────────────────────────
    var displayText by remember { mutableStateOf("0") }
    var firstOperand by remember { mutableDoubleStateOf(0.0) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var resetDisplayOnNextDigit by remember { mutableStateOf(false) }
    var lastResult by remember { mutableDoubleStateOf(0.0) }
    var hasDecimal by remember { mutableStateOf(false) }

    // ── Secret tracking ──────────────────────────────────────────────────
    var secretBuffer by remember { mutableStateOf("") }

    // ── Helpers ──────────────────────────────────────────────────────────

    fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble() && !value.isInfinite() && !value.isNaN()) {
            value.toLong().toString()
        } else {
            val formatted = "%.10f".format(value).trimEnd('0').trimEnd('.')
            formatted
        }
    }

    fun evaluate(a: Double, op: String, b: Double): Double {
        return when (op) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else Double.NaN
            else -> b
        }
    }

    fun onDigit(digit: String) {
        // Track secret buffer
        secretBuffer += digit
        if (secretBuffer.length > 4) {
            secretBuffer = secretBuffer.takeLast(4)
        }
        if (secretBuffer == "0000") {
            onSecretUnlocked()
            return
        }

        if (resetDisplayOnNextDigit) {
            displayText = digit
            resetDisplayOnNextDigit = false
            hasDecimal = false
        } else {
            if (displayText == "0" && digit != "0") {
                displayText = digit
            } else if (displayText == "0" && digit == "0") {
                // keep "0"
            } else {
                displayText += digit
            }
        }
    }

    fun onDecimal() {
        secretBuffer = ""
        if (resetDisplayOnNextDigit) {
            displayText = "0."
            resetDisplayOnNextDigit = false
            hasDecimal = true
            return
        }
        if (!hasDecimal) {
            displayText += "."
            hasDecimal = true
        }
    }

    fun onOperator(op: String) {
        secretBuffer = ""
        val current = displayText.toDoubleOrNull() ?: 0.0
        if (pendingOperator != null && !resetDisplayOnNextDigit) {
            val result = evaluate(firstOperand, pendingOperator!!, current)
            firstOperand = result
            displayText = formatNumber(result)
        } else {
            firstOperand = current
        }
        pendingOperator = op
        resetDisplayOnNextDigit = true
        hasDecimal = false
    }

    fun onEquals() {
        secretBuffer = ""
        val current = displayText.toDoubleOrNull() ?: 0.0
        if (pendingOperator != null) {
            val result = evaluate(firstOperand, pendingOperator!!, current)
            lastResult = result
            displayText = if (result.isNaN()) "Error" else formatNumber(result)
            pendingOperator = null
            resetDisplayOnNextDigit = true
            hasDecimal = displayText.contains(".")
        }
    }

    fun onClear() {
        secretBuffer = ""
        displayText = "0"
        firstOperand = 0.0
        pendingOperator = null
        resetDisplayOnNextDigit = false
        hasDecimal = false
        lastResult = 0.0
    }

    fun onToggleSign() {
        secretBuffer = ""
        val current = displayText.toDoubleOrNull() ?: return
        val toggled = -current
        displayText = formatNumber(toggled)
    }

    fun onPercent() {
        secretBuffer = ""
        val current = displayText.toDoubleOrNull() ?: return
        val result = current / 100.0
        displayText = formatNumber(result)
        resetDisplayOnNextDigit = true
    }

    // ── UI ───────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .systemBarsPadding()
    ) {
        // Display area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(DisplayBackground)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = displayText,
                color = DisplayTextColor,
                fontSize = if (displayText.length > 9) 48.sp else 72.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        // Button grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: AC  +/-  %  ÷
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("AC", FunctionButtonColor, FunctionTextColor, Modifier.weight(1f)) { onClear() }
                CalcButton("+/−", FunctionButtonColor, FunctionTextColor, Modifier.weight(1f)) { onToggleSign() }
                CalcButton("%", FunctionButtonColor, FunctionTextColor, Modifier.weight(1f)) { onPercent() }
                CalcButton("÷", OperatorButtonColor, OperatorTextColor, Modifier.weight(1f)) { onOperator("÷") }
            }

            // Row 2: 7  8  9  ×
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("7", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("7") }
                CalcButton("8", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("8") }
                CalcButton("9", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("9") }
                CalcButton("×", OperatorButtonColor, OperatorTextColor, Modifier.weight(1f)) { onOperator("×") }
            }

            // Row 3: 4  5  6  −
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("4", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("4") }
                CalcButton("5", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("5") }
                CalcButton("6", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("6") }
                CalcButton("−", OperatorButtonColor, OperatorTextColor, Modifier.weight(1f)) { onOperator("−") }
            }

            // Row 4: 1  2  3  +
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("1", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("1") }
                CalcButton("2", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("2") }
                CalcButton("3", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDigit("3") }
                CalcButton("+", OperatorButtonColor, OperatorTextColor, Modifier.weight(1f)) { onOperator("+") }
            }

            // Row 5: 0 (wide)  .  =
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("0", DigitButtonColor, DigitTextColor, Modifier.weight(2f), isWide = true) { onDigit("0") }
                CalcButton(".", DigitButtonColor, DigitTextColor, Modifier.weight(1f)) { onDecimal() }
                CalcButton("=", OperatorButtonColor, OperatorTextColor, Modifier.weight(1f)) { onEquals() }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(if (isWide) 2.15f else 1f)
            .clip(if (isWide) MaterialTheme.shapes.extraLarge else CircleShape),
        shape = if (isWide) MaterialTheme.shapes.extraLarge else CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = if (isWide) TextAlign.Start else TextAlign.Center,
            modifier = if (isWide) Modifier.padding(start = 28.dp) else Modifier
        )
    }
}
