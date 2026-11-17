/*
 * PSST (D81): QR code device verification on the verification screens (outgoing and incoming).
 *
 * Shown once the request is ready. Depending on what the other device supports (its methods in the request/ready
 * event): our QR code for it to scan, a "Scan QR code" button for its code, and always "Compare emoji". If the other
 * device knows only emoji (Element X iOS), nothing is shown and the upstream emoji flow runs as before.
 * Standard Matrix QR verification (m.qr_code.show.v1 / m.qr_code.scan.v1 / m.reciprocate.v1) as Element Web/Desktop.
 */
package io.element.android.features.verifysession.impl.psst

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.verifysession.impl.R
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.psst.PsstCancelInfo
import io.element.android.libraries.matrix.api.verification.psst.PsstQrOptions
import io.element.android.libraries.matrix.api.verification.psst.PsstQrProgress
import io.element.android.libraries.matrix.api.verification.psst.PsstQrScanResult
import io.element.android.libraries.matrix.api.verification.psst.PsstQrVerification
import io.element.android.libraries.qrcode.PsstBinaryQrCodeImage
import io.element.android.libraries.qrcode.QrCodeCameraView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val NO_QR = PsstQrOptions(canShowQrCode = false, canScanQrCode = false)

@Immutable
class PsstQrUi(
    val service: PsstQrVerification?,
    val options: PsstQrOptions,
    val qrCode: ByteArray?,
    val progress: PsstQrProgress,
) {
    /** QR verification can be offered (or is running) for the current request. */
    val available: Boolean get() = service != null && (options.any || progress !is PsstQrProgress.None)

    /** A QR verification is past the choice: show its progress instead of the upstream content. */
    val inProgress: Boolean get() = progress !is PsstQrProgress.None && progress !is PsstQrProgress.Cancelled

    companion object {
        val NONE = PsstQrUi(null, NO_QR, null, PsstQrProgress.None)
    }
}

/** Options of the other device and our QR code, loaded while [active] (the request is ready). */
@Composable
fun rememberPsstQrUi(sessionVerificationService: SessionVerificationService, active: Boolean): PsstQrUi {
    val service = remember(sessionVerificationService) { sessionVerificationService as? PsstQrVerification } ?: return PsstQrUi.NONE
    val progress by service.psstQrProgress.collectAsState()
    var options by remember { mutableStateOf(NO_QR) }
    var qrCode by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        // the other device's methods come with its request / ready event; give the SDK a moment
        repeat(10) {
            val o = service.psstQrOptions()
            if (o.any) {
                options = o
                if (o.canShowQrCode && qrCode == null) qrCode = service.psstGenerateQrCode()
                return@LaunchedEffect
            }
            delay(300.milliseconds)
        }
    }
    return PsstQrUi(service, options, qrCode, progress)
}

/** Cancel code of the last cancelled flow, read when a "failed" screen appears. */
fun psstCancelInfo(sessionVerificationService: SessionVerificationService): PsstCancelInfo? =
    (sessionVerificationService as? PsstQrVerification)?.psstLastCancelCode()

/** Whether the other device of the current (accepted) request can use a QR code; waits briefly for its methods. */
suspend fun psstQrPossible(sessionVerificationService: SessionVerificationService): Boolean {
    val service = sessionVerificationService as? PsstQrVerification ?: return false
    repeat(5) {
        if (service.psstQrOptions().any) return true
        delay(200.milliseconds)
    }
    return false
}

private sealed interface ScanUi {
    data object Idle : ScanUi
    data object Scanning : ScanUi
    data object Checking : ScanUi
    data object PermissionDenied : ScanUi
    data class Error(val result: PsstQrScanResult) : ScanUi
}

@Composable
fun PsstQrVerificationSection(
    ui: PsstQrUi,
    onCompareEmoji: (() -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = ui.service ?: return
    if (!ui.available) return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var scan by remember { mutableStateOf<ScanUi>(ScanUi.Idle) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scan = if (granted) ScanUi.Scanning else ScanUi.PermissionDenied
    }
    fun startScanning() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) scan = ScanUi.Scanning else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (ui.progress) {
            PsstQrProgress.Scanned -> {
                Info(stringResource(R.string.psst_qr_scanned_question))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.psst_qr_scanned_yes),
                    onClick = { scope.launch { service.psstConfirmQrScanned() } },
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.psst_qr_scanned_no),
                    onClick = onCancel,
                )
                return@Column
            }
            PsstQrProgress.Reciprocated -> {
                Waiting(stringResource(R.string.psst_qr_reciprocated))
                return@Column
            }
            PsstQrProgress.Confirmed, PsstQrProgress.Done -> {
                Waiting(stringResource(R.string.psst_qr_finishing))
                return@Column
            }
            PsstQrProgress.None, is PsstQrProgress.Cancelled -> Unit
        }

        when (val s = scan) {
            ScanUi.Scanning, ScanUi.Checking -> {
                Info(stringResource(R.string.psst_qr_scan_hint))
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    QrCodeCameraView(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        isScanning = s == ScanUi.Scanning,
                        onScanQrCode = { bytes ->
                            if (scan != ScanUi.Scanning) return@QrCodeCameraView
                            scan = ScanUi.Checking
                            scope.launch {
                                val result = service.psstScanQrCode(bytes)
                                scan = if (result == PsstQrScanResult.Started) ScanUi.Idle else ScanUi.Error(result)
                            }
                        },
                    )
                    if (s == ScanUi.Checking) CircularProgressIndicator()
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.psst_qr_scan_stop),
                    onClick = { scan = ScanUi.Idle },
                )
            }
            else -> {
                if (s is ScanUi.Error) Problem(stringResource(s.result.messageRes()))
                if (s == ScanUi.PermissionDenied) {
                    Problem(stringResource(R.string.psst_qr_camera_denied))
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.psst_qr_open_settings),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                }
                ui.qrCode?.let { code ->
                    Info(stringResource(if (ui.options.canScanQrCode) R.string.psst_qr_show_hint_both else R.string.psst_qr_show_hint))
                    PsstBinaryQrCodeImage(data = code, contentDescription = stringResource(R.string.psst_qr_code_description), size = 220.dp)
                }
                if (ui.options.canScanQrCode) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(if (s is ScanUi.Error) R.string.psst_qr_scan_again else R.string.psst_qr_scan),
                        onClick = ::startScanning,
                    )
                }
                if (onCompareEmoji != null) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.psst_qr_compare_emoji),
                        onClick = onCompareEmoji,
                    )
                }
            }
        }
    }
    // A scan that ended the request (cancel on the other side) must not leave the camera open
    LaunchedEffect(ui.progress) {
        if (ui.progress !is PsstQrProgress.None) scan = ScanUi.Idle
    }
}

@Composable
private fun Info(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        textAlign = TextAlign.Center,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun Problem(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        textAlign = TextAlign.Center,
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textCriticalPrimary,
    )
}

@Composable
private fun Waiting(text: String) {
    CircularProgressIndicator(modifier = Modifier.size(32.dp))
    Info(text)
}

private fun PsstQrScanResult.messageRes(): Int = when (this) {
    PsstQrScanResult.InvalidCode -> R.string.psst_qr_error_invalid
    PsstQrScanResult.KeyMismatch -> R.string.psst_qr_error_mismatch
    PsstQrScanResult.NotApplicable -> R.string.psst_qr_error_not_active
    PsstQrScanResult.Failed, PsstQrScanResult.Started -> R.string.psst_qr_error_failed
}

/** Why the verification stopped (shown on the "failed" screens), or nothing when unknown. */
@Composable
fun PsstVerificationCancelReason(info: PsstCancelInfo?, modifier: Modifier = Modifier) {
    info ?: return
    Text(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        text = stringResource(info.messageRes()),
        textAlign = TextAlign.Center,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textPrimary,
    )
}

private fun PsstCancelInfo.messageRes(): Int = when (code) {
    "m.timeout" -> R.string.psst_cancel_timeout
    "m.user" -> if (cancelledByUs) R.string.psst_cancel_by_us else R.string.psst_cancel_by_other
    "m.key_mismatch", "m.mismatched_sas", "m.mismatched_commitment", "m.user_mismatch" -> R.string.psst_cancel_mismatch
    "m.accepted" -> R.string.psst_cancel_accepted_elsewhere
    "m.unknown_method", "m.unexpected_message", "m.unknown_transaction", "m.invalid_message" -> R.string.psst_cancel_unsupported
    else -> R.string.psst_cancel_other
}
