@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.meow.reminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.data.*

@Composable
fun NotesScreen(vm: MeowViewModel, edit: (String) -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var archive by remember { mutableStateOf(false) }
    val visible = notes.filter { it.archived == archive && (it.title.contains(search, true) || it.body.contains(search, true)) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (archive) "Архив заметок" else "Заметки", style = MaterialTheme.typography.titleLarge) }, actions = {
            IconButton(onClick = { archive = !archive }) { Icon(if (archive) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (archive) "Открыть заметки" else "Открыть архив") }
            IconButton(onClick = { val note = Note(); vm.save(note); edit(note.id) }) { Icon(Icons.Outlined.Add, "Создать заметку") }
        })
        LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(165.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp) {
            item(span = StaggeredGridItemSpan.FullLine) { Field(search, "Поиск в заметках", { search = it }) }
            item(span = StaggeredGridItemSpan.FullLine) { Button(onClick = { val note = Note(); vm.save(note); edit(note.id) }, modifier = Modifier.fillMaxWidth()) { Text("Новая заметка") } }
            if (visible.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) { EmptyState("Место для ваших мыслей", "Тексты, списки и маленькие идеи — без искусственных лимитов.", settings.cat) }
            items(visible, key = { it.id }) { note ->
                Card(onClick = { edit(note.id) }, colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background.red < .3f) MaterialTheme.colorScheme.surfaceContainerHigh else pastels[note.color.coerceIn(0, 4)]), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (note.pinned) Icon(Icons.Outlined.PushPin, "Закреплено", modifier = Modifier.size(18.dp))
                        if (note.title.isNotBlank()) Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                        Text(note.body.ifBlank { "Пустая заметка" }, maxLines = 12, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun NoteEditor(vm: MeowViewModel, id: String, back: () -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val original = notes.find { it.id == id }
    if (original == null) { Page("Заметка", back) { Text("Загрузка…") }; return }
    var note by remember(id) { mutableStateOf(original) }
    var delete by remember { mutableStateOf(false) }
    var item by remember { mutableStateOf("") }
    fun change(value: Note) { note = value; vm.save(value) }
    Page("Заметка", back, actions = {
        IconButton(onClick = { change(note.copy(pinned = !note.pinned)) }) { Icon(if (note.pinned) Icons.Outlined.PushPin else Icons.Outlined.PushPin, "Закрепить / открепить", tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        IconButton(onClick = { change(note.copy(archived = !note.archived)); back() }) { Icon(if (note.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, "В архив / из архива") }
    }) {
        Text("Все изменения сохраняются автоматически", style = MaterialTheme.typography.labelMedium)
        Field(note.title, "Заголовок", { change(note.copy(title = it)) })
        Toggle("Список с галочками", note.checklist) { change(note.copy(checklist = it)) }
        if (!note.checklist) Field(note.body, "Ваш текст", { change(note.copy(body = it)) }, singleLine = false)
        else {
            val lines = if (note.body.isEmpty()) emptyList() else note.body.lines()
            lines.forEachIndexed { index, line ->
                val checked = line.startsWith("[x] ")
                Row {
                    Checkbox(checked, onCheckedChange = { done ->
                        val content = line.removePrefix("[x] ").removePrefix("[ ] ")
                        change(note.copy(body = lines.toMutableList().apply { set(index, (if (done) "[x] " else "[ ] ") + content) }.joinToString("\n")))
                    })
                    OutlinedTextField(value = line.removePrefix("[x] ").removePrefix("[ ] "), onValueChange = { value ->
                        change(note.copy(body = lines.toMutableList().apply { set(index, (if (checked) "[x] " else "[ ] ") + value) }.joinToString("\n")))
                    }, modifier = Modifier.weight(1f), label = { Text("Пункт ${index + 1}") })
                    IconButton(onClick = { change(note.copy(body = lines.toMutableList().apply { removeAt(index) }.joinToString("\n"))) }) { Icon(Icons.Outlined.Close, "Удалить пункт") }
                }
            }
            Field(item, "Новый пункт", { item = it })
            OutlinedButton(onClick = { if (item.isNotBlank()) { change(note.copy(body = (lines + "[ ] $item").joinToString("\n"))); item = "" } }) { Text("Добавить пункт") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pastels.forEachIndexed { index, _ -> FilterChip(selected = note.color == index, onClick = { change(note.copy(color = index)) }, label = { Text(listOf("Лаванда", "Роза", "Небо", "Персик", "Шалфей")[index]) }) } }
        TextButton(onClick = { delete = true }) { Text("Удалить заметку", color = MaterialTheme.colorScheme.error) }
    }
    if (delete) Confirm("Удалить заметку?", "Это действие нельзя отменить.", { delete = false }) { vm.task { vm.repo.dao.deleteNote(id) }; back() }
}
