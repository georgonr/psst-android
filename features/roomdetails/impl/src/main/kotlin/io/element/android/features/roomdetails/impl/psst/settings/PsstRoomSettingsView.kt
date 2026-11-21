/*
 * PSST (D95, D96): rows of the room details, see PsstRoomSettingsPresenter.
 */
package io.element.android.features.roomdetails.impl.psst.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.dialogs.ListOption
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.room.psst.PsstRetention
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.strings.psstRetentionLabel
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PsstRoomSettingsSection(state: PsstRoomSettingsState) {
    var showRetentionPicker by remember { mutableStateOf(false) }
    PreferenceCategory {
        ListItem(
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Time())),
            content = { Text(stringResource(CommonStrings.psst_disappearing_messages)) },
            supportingContent = {
                Text(
                    if (state.canChangeRetention) {
                        stringResource(psstRetentionLabel(state.retentionMaxLifetimeMs))
                    } else {
                        stringResource(psstRetentionLabel(state.retentionMaxLifetimeMs)) + "\n" +
                            stringResource(CommonStrings.psst_room_setting_read_only)
                    }
                )
            },
            enabled = state.canChangeRetention && !state.isSaving,
            onClick = if (state.canChangeRetention) {
                { showRetentionPicker = true }
            } else {
                null
            },
        )
        ListItem(
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.VisibilityOff())),
            content = { Text(stringResource(CommonStrings.psst_screen_protection)) },
            supportingContent = {
                Text(
                    if (state.canChangeScreenProtection) {
                        stringResource(CommonStrings.psst_screen_protection_explanation)
                    } else {
                        stringResource(CommonStrings.psst_screen_protection_explanation) + "\n" +
                            stringResource(CommonStrings.psst_room_setting_read_only)
                    }
                )
            },
            trailingContent = ListItemContent.Switch(
                checked = state.screenProtection,
                enabled = state.canChangeScreenProtection && !state.isSaving,
            ),
            enabled = state.canChangeScreenProtection && !state.isSaving,
            onClick = if (state.canChangeScreenProtection) {
                { state.eventSink(PsstRoomSettingsEvent.SetScreenProtection(!state.screenProtection)) }
            } else {
                null
            },
        )
    }
    if (showRetentionPicker) {
        val values: List<Long?> = listOf(null) + PsstRetention.choices
        val options = values.map { ListOption(title = stringResource(psstRetentionLabel(it))) }.toImmutableList()
        SingleSelectionDialog(
            title = stringResource(CommonStrings.psst_disappearing_messages),
            subtitle = stringResource(CommonStrings.psst_disappearing_messages_explanation),
            options = options,
            initialSelection = values.indexOf(state.retentionMaxLifetimeMs).takeIf { it >= 0 },
            onSelectOption = { index ->
                showRetentionPicker = false
                state.eventSink(PsstRoomSettingsEvent.SetRetention(values[index]))
            },
            onDismissRequest = { showRetentionPicker = false },
        )
    }
    if (state.hasError) {
        ErrorDialog(
            content = stringResource(CommonStrings.error_unknown),
            onSubmit = { state.eventSink(PsstRoomSettingsEvent.DismissError) },
        )
    }
}
