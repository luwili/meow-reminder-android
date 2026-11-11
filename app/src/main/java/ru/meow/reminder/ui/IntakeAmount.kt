package ru.meow.reminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/** A blank amount is left blank so callers can distinguish unknown from an actual value. */
fun parseIntakeAmount(text: String): Double? = text.trim().replace(',', '.')
    .toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

fun formatIntakeAmount(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntakeAmountField(value: String, onValueChange: (String) -> Unit, isSyrup: Boolean, required: Boolean = true) {
    val valid = parseIntakeAmount(value) != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Количество на один приём")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (isSyrup) listOf("2,5", "5", "10") else listOf("0,5", "1", "2")).forEach { amount ->
                FilterChip(selected = value == amount, onClick = { onValueChange(amount) }, label = { Text(amount) })
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (isSyrup) "Миллилитров за приём" else "Таблеток за приём") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = value.isNotBlank() && !valid,
            supportingText = { if (value.isNotBlank() && !valid) Text("Введите количество больше нуля") else if (!required) Text("Если не указано, используется 1 таблетка") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
    }
}
