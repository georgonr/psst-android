/*
 * PSST (D80): node of "Hľadať v konverzácii" (Room details → Search messages).
 */
package io.element.android.features.roomdetails.impl.psst.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.appyx.launchMolecule
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId

@ContributesNode(RoomScope::class)
@AssistedInject
class PsstRoomSearchNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: PsstRoomSearchPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun openMessage(eventId: EventId)
    }

    private val callback: Callback = callback()
    private val stateFlow = launchMolecule { presenter.present() }

    @Composable
    override fun View(modifier: Modifier) {
        val state by stateFlow.collectAsState()
        PsstRoomSearchView(
            state = state,
            onBackClick = ::navigateUp,
            onResultClick = callback::openMessage,
            modifier = modifier,
        )
    }
}
