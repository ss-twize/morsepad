package com.morsepad.app.ui.notes

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.morsepad.app.R
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.core.notes.Note
import com.morsepad.core.notes.NoteRepository
import com.morsepad.core.notes.NoteSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(private val notes: NoteRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Null until the first database emission. */
    val visibleNotes: StateFlow<List<Note>?> =
        combine(notes.observeNotes(), _query) { all, q -> NoteSearch.filter(all, q) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun delete(id: Long) {
        viewModelScope.launch { notes.delete(id) }
    }
}

@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalPadColors.current
    val notes by viewModel.visibleNotes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Note?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                Spacer(Modifier.width(24.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.list_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f).testTag(TAG_SEARCH),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title), tint = colors.text)
                }
            }
            HorizontalDivider(color = colors.keyBorder)

            val list = notes
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(if (query.isBlank()) R.string.list_empty else R.string.list_nothing_found)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(TAG_NOTE_LIST),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(list, key = { it.id }) { note ->
                        NoteRow(note, onOpen = { onOpen(note.id) }, onDelete = { pendingDelete = note })
                        HorizontalDivider(color = colors.keyBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.list_new_note)) },
            containerColor = colors.keyActive,
            contentColor = colors.labelActive,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).testTag(TAG_NEW_NOTE),
        )
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(note.displayTitle.ifBlank { stringResource(R.string.note_untitled) }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(note.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag(TAG_CONFIRM_DELETE),
                ) { Text(stringResource(R.string.delete_confirm), color = colors.warning) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun NoteRow(note: Note, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalPadColors.current
    val context = LocalContext.current
    val updated = remember(note.updatedAt) {
        DateUtils.formatDateTime(
            context,
            note.updatedAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                note.displayTitle.ifBlank { stringResource(R.string.note_untitled) },
                color = colors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.content.length > note.displayTitle.length) {
                Text(note.content, color = colors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(updated, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("$TAG_DELETE_PREFIX${note.id}")) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_title), tint = colors.textSecondary)
        }
    }
}

@Composable
private fun EmptyState(message: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(message), color = LocalPadColors.current.textSecondary, fontSize = 16.sp)
    }
}

const val TAG_NEW_NOTE = "new_note"
const val TAG_SEARCH = "search"
const val TAG_NOTE_LIST = "note_list"
const val TAG_DELETE_PREFIX = "delete_"
const val TAG_CONFIRM_DELETE = "confirm_delete"
