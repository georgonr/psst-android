/*
 * PSST (D80): test double of PsstSearchIndexing.
 */
package io.element.android.libraries.matrix.test.search.psst

import io.element.android.libraries.matrix.api.search.psst.PsstSearchIndexing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePsstSearchIndexing(
    private val sweepDue: Boolean = false,
) : PsstSearchIndexing {
    override val isIndexing: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun runWhileVisible() = Unit
    override fun isSweepDue(): Boolean = sweepDue
    override suspend fun observe() = Unit
}
