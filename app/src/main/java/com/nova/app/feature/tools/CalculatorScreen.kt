package com.nova.app.feature.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaMonoFont
import com.nova.app.ui.theme.NovaSurfaceRaised
import com.nova.app.ui.theme.NovaTextPrimary

/** Minimal recursive-descent evaluator for + - * / ( ) and decimals. No eval(), no libraries. */
private class ExpressionEvaluator(private val expr: String) {
    private var pos = 0
    private fun peek(): Char? = if (pos < expr.length) expr[pos] else null
    private fun consumeWhitespace() { while (peek() == ' ') pos++ }

    fun evaluate(): Double {
        consumeWhitespace()
        val result = parseExpression()
        consumeWhitespace()
        if (pos != expr.length) throw IllegalArgumentException("Unexpected character at $pos")
        return result
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            consumeWhitespace()
            when (peek()) {
                '+' -> { pos++; value += parseTerm() }
                '-' -> { pos++; value -= parseTerm() }
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (true) {
            consumeWhitespace()
            when (peek()) {
                '*' -> { pos++; value *= parseFactor() }
                '/' -> {
                    pos++
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    value /= divisor
                }
                else -> return value
            }
        }
    }

    private fun parseFactor(): Double {
        consumeWhitespace()
        if (peek() == '-') { pos++; return -parseFactor() }
        if (peek() == '(') {
            pos++
            val value = parseExpression()
            consumeWhitespace()
            if (peek() != ')') throw IllegalArgumentException("Missing closing parenthesis")
            pos++
            return value
        }
        val start = pos
        while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
        if (start == pos) throw IllegalArgumentException("Expected number at $pos")
        return expr.substring(start, pos).toDouble()
    }
}

@Composable
fun CalculatorScreen() {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    var hasError by remember { mutableStateOf(false) }

    fun append(token: String) {
        expression += token
        hasError = false
    }

    fun evaluate() {
        if (expression.isBlank()) return
        try {
            val value = ExpressionEvaluator(expression).evaluate()
            result = if (value == value.toLong().toDouble()) value.toLong().toString() else "%.6f".format(value).trimEnd('0').trimEnd('.')
            hasError = false
        } catch (e: Exception) {
            result = "Error"
            hasError = true
        }
    }

    val buttons = listOf(
        "7", "8", "9", "÷",
        "4", "5", "6", "×",
        "1", "2", "3", "−",
        "C", "0", ".", "+"
    )

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Calculator", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        NovaCard {
            Text(
                expression.ifBlank { "0" },
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = NovaMonoFont),
                color = NovaTextPrimary.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                result,
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = NovaMonoFont, fontWeight = FontWeight.Bold),
                color = if (hasError) MaterialTheme.colorScheme.error else NovaAccent,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(buttons) { label ->
                CalcButton(label) {
                    when (label) {
                        "C" -> { expression = ""; result = "0"; hasError = false }
                        "÷" -> append("/")
                        "×" -> append("*")
                        "−" -> append("-")
                        else -> append(label)
                    }
                }
            }
            item {
                CalcButton("=", accent = true) { evaluate() }
            }
        }
    }
}

@Composable
private fun CalcButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .aspectRatio(1.4f)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (accent) NovaAccent else NovaSurfaceRaised,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = if (accent) androidx.compose.ui.graphics.Color(0xFF0A0D12) else NovaTextPrimary
            )
        }
    }
}
