package com.nova.app.feature.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- Timestamp converter ----------

@Composable
fun TimestampConverterScreen() {
    var epochInput by remember { mutableStateOf(System.currentTimeMillis().toString()) }
    var humanOutput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val formatterUtc = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    val formatterLocal = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss (zzz)", Locale.getDefault()) }

    fun convert() {
        error = false
        humanOutput = try {
            val millis = epochInput.trim().toLong().let { if (it < 10_000_000_000L) it * 1000 else it }
            val date = Date(millis)
            "${formatterUtc.format(date)}\n${formatterLocal.format(date)}"
        } catch (e: Exception) {
            error = true
            ""
        }
    }

    LaunchedEffect(Unit) { convert() }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Timestamp Converter", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        ToolTextField(
            value = epochInput,
            onValueChange = { epochInput = it; convert() },
            label = "Unix epoch (seconds or ms)",
            minLines = 1,
            isError = error
        )

        if (!error) ToolResultCard(title = "HUMAN-READABLE (UTC / LOCAL)", value = humanOutput)
        else NovaCard { Text("Not a valid epoch value", color = MaterialTheme.colorScheme.error) }

        OutlinedButton(onClick = { epochInput = System.currentTimeMillis().toString(); convert() }) {
            Text("Use current time")
        }
    }
}

// ---------- Unit converter ----------

private data class UnitDef(val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

private data class UnitCategory(val name: String, val baseUnit: String, val units: List<UnitDef>)

private val categories = listOf(
    UnitCategory("Length", "meters", listOf(
        UnitDef("Meters", { it }, { it }),
        UnitDef("Kilometers", { it * 1000 }, { it / 1000 }),
        UnitDef("Miles", { it * 1609.344 }, { it / 1609.344 }),
        UnitDef("Feet", { it * 0.3048 }, { it / 0.3048 }),
        UnitDef("Inches", { it * 0.0254 }, { it / 0.0254 }),
        UnitDef("Centimeters", { it * 0.01 }, { it / 0.01 })
    )),
    UnitCategory("Mass", "kilograms", listOf(
        UnitDef("Kilograms", { it }, { it }),
        UnitDef("Grams", { it * 0.001 }, { it / 0.001 }),
        UnitDef("Pounds", { it * 0.45359237 }, { it / 0.45359237 }),
        UnitDef("Ounces", { it * 0.028349523 }, { it / 0.028349523 })
    )),
    UnitCategory("Temperature", "celsius", listOf(
        UnitDef("Celsius", { it }, { it }),
        UnitDef("Fahrenheit", { (it - 32) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32 }),
        UnitDef("Kelvin", { it - 273.15 }, { it + 273.15 })
    )),
    UnitCategory("Data", "bytes", listOf(
        UnitDef("Bytes", { it }, { it }),
        UnitDef("Kilobytes", { it * 1024 }, { it / 1024 }),
        UnitDef("Megabytes", { it * 1024 * 1024 }, { it / (1024 * 1024) }),
        UnitDef("Gigabytes", { it * 1024.0 * 1024 * 1024 }, { it / (1024.0 * 1024 * 1024) })
    ))
)

@Composable
fun UnitConverterScreen() {
    var categoryIndex by remember { mutableStateOf(0) }
    var fromIndex by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("1") }

    val category = categories[categoryIndex]

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Unit Converter", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEachIndexed { i, cat ->
                FilterChip(
                    selected = i == categoryIndex,
                    onClick = { categoryIndex = i; fromIndex = 0 },
                    label = { Text(cat.name) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            category.units.forEachIndexed { i, unit ->
                FilterChip(
                    selected = i == fromIndex,
                    onClick = { fromIndex = i },
                    label = { Text(unit.name) }
                )
            }
        }

        ToolTextField(value = input, onValueChange = { input = it }, label = "Value in ${category.units[fromIndex].name}", minLines = 1)

        val baseValue = input.toDoubleOrNull()?.let { category.units[fromIndex].toBase(it) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(category.units.filterIndexed { i, _ -> i != fromIndex }) { unit ->
                val converted = baseValue?.let { unit.fromBase(it) }
                ToolResultCard(
                    title = unit.name.uppercase(),
                    value = converted?.let { "%.6g".format(it).trimEnd('0').trimEnd('.') } ?: "—"
                )
            }
        }
    }
}
