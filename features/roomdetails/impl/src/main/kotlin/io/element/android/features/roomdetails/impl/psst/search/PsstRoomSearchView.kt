/*
 * PSST (D80): "Hľadať v konverzácii" screen.
 */
package io.element.android.features.roomdetails.impl.psst.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.roomdetails.impl.R
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.niceClickable
import io.element.android.libraries.designsystem.psst.PsstSearchHighlight
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsstRoomSearchView(
    state: PsstRoomSearchState,
    onBackClick: () -> Unit,
    onResultClick: (EventId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBackClick) },
                title = { SearchField(state) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            HorizontalDivider(color = ElementTheme.colors.borderInteractivePrimary)
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            val listState = rememberLazyListState()
            val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 5 } }
            LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.items.isNotEmpty() && !state.endReached) state.eventSink(PsstRoomSearchEvent.LoadMore) }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (state.isIndexing) {
                    item(key = "indexing") { Hint(stringResource(R.string.psst_search_indexing)) }
                }
                when {
                    state.searchedQuery.isEmpty() -> item(key = "start") { Hint(stringResource(R.string.psst_room_search_start)) }
                    state.items.isEmpty() && !state.isLoading -> item(key = "empty") {
                        Hint(stringResource(R.string.psst_room_search_empty, state.searchedQuery))
                    }
                    else -> items(state.items, key = { it.eventId.value }) { item ->
                        ResultRow(item, state.searchedQuery, onClick = { onResultClick(item.eventId) })
                        HorizontalDivider(color = ElementTheme.colors.separatorSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun ResultRow(item: PsstRoomSearchItem, query: String, onClick: () -> Unit) {
    val highlight = SpanStyle(color = ElementTheme.colors.textActionAccent, fontWeight = FontWeight.Bold)
    Column(
        modifier = Modifier.fillMaxWidth().niceClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                modifier = Modifier.weight(1f),
                text = item.senderName ?: stringResource(CommonStrings.common_you),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ElementTheme.typography.fontBodyMdMedium,
            )
            Text(
                text = item.date,
                maxLines = 1,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Text(
            text = remember(item.text, query) { PsstSearchHighlight.snippet(item.text, query, highlight) },
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(state: PsstRoomSearchState) {
    val focusRequester = remember { FocusRequester() }
    val label = stringResource(R.string.psst_room_search_hint)
    FilledTextField(
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { contentDescription = label },
        state = state.queryState,
        placeholder = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        trailingIcon = if (state.queryState.text.isNotEmpty()) {
            @Composable {
                IconButton(
                    modifier = Modifier.clip(CircleShape).size(20.dp),
                    onClick = { state.eventSink(PsstRoomSearchEvent.Clear) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ElementTheme.colors.iconSecondary,
                        contentColor = ElementTheme.colors.iconOnSolidPrimary,
                    ),
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(CommonStrings.a11y_clear_search_field),
                    )
                }
            }
        } else {
            null
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
