/*
 * PSST (D81): QR code device verification (standard Matrix "QR codes" flow, compatible with Element Web/Desktop).
 * Implemented by the SessionVerificationService of this app (RustSessionVerificationService); the verification screens
 * get it with `sessionVerificationService as? PsstQrVerification`. See docs/android-qr-verification.md.
 */
package io.element.android.libraries.matrix.api.verification.psst

import kotlinx.coroutines.flow.StateFlow

interface PsstQrVerification {
    /** Progress of a QR code verification of the current request. */
    val psstQrProgress: StateFlow<PsstQrProgress>

    /** QR roles possible with the other device; both false before the request is ready or if it only knows emoji. */
    suspend fun psstQrOptions(): PsstQrOptions

    /** Binary QR data to show (byte mode), or null when the other device cannot scan. */
    suspend fun psstGenerateQrCode(): ByteArray?

    /** The other device's QR code scanned with the camera. */
    suspend fun psstScanQrCode(data: ByteArray): PsstQrScanResult

    /** The user confirmed that the other device shows a successful scan of our code. */
    suspend fun psstConfirmQrScanned()

    /** Cancel code of the last cancelled request/emoji/QR flow (e.g. `m.timeout`, `m.user`, `m.key_mismatch`). */
    fun psstLastCancelCode(): PsstCancelInfo?
}

data class PsstQrOptions(val canShowQrCode: Boolean, val canScanQrCode: Boolean) {
    val any: Boolean get() = canShowQrCode || canScanQrCode
}

data class PsstCancelInfo(val code: String, val cancelledByUs: Boolean)

sealed interface PsstQrScanResult {
    data object Started : PsstQrScanResult
    data object InvalidCode : PsstQrScanResult
    data object KeyMismatch : PsstQrScanResult
    data object NotApplicable : PsstQrScanResult
    data object Failed : PsstQrScanResult
}

sealed interface PsstQrProgress {
    data object None : PsstQrProgress

    /** The other device scanned our code: ask the user to confirm. */
    data object Scanned : PsstQrProgress

    /** We scanned their code: waiting for the other device to confirm. */
    data object Reciprocated : PsstQrProgress

    /** We confirmed their scan: finishing. */
    data object Confirmed : PsstQrProgress
    data object Done : PsstQrProgress
    data class Cancelled(val info: PsstCancelInfo) : PsstQrProgress
}
