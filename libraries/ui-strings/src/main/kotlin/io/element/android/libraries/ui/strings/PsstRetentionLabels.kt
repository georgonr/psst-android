/*
 * PSST (D95): labels of the disappearing-messages values (m.room.retention max_lifetime in ms).
 */
package io.element.android.libraries.ui.strings

/** The label of a `max_lifetime`; null = off. Values other psst clients would not offer are shown as "Custom". */
fun psstRetentionLabel(maxLifetimeMs: Long?): Int = when (maxLifetimeMs) {
    null -> R.string.psst_disappearing_messages_off
    300_000L -> R.string.psst_disappearing_messages_5_minutes
    3_600_000L -> R.string.psst_disappearing_messages_1_hour
    86_400_000L -> R.string.psst_disappearing_messages_1_day
    604_800_000L -> R.string.psst_disappearing_messages_1_week
    else -> R.string.psst_disappearing_messages_custom
}
