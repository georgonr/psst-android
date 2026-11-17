/*
 * PSST (D81): QR code for binary data (Matrix device verification codes are binary, byte mode). The bytes are passed
 * to ZXing as ISO-8859-1 characters without a charset hint, so they are encoded 1:1 in byte mode and without an ECI
 * header — the way Element Web/Desktop encode and read them.
 */
package io.element.android.libraries.qrcode

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.element.android.libraries.designsystem.utils.ForceMaxBrightness

fun psstBinaryQrBitmap(data: ByteArray, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        String(data, Charsets.ISO_8859_1),
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
    )
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

@Composable
fun PsstBinaryQrCodeImage(
    data: ByteArray,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
) {
    ForceMaxBrightness()
    val image = remember(data) { psstBinaryQrBitmap(data, 720).asImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = contentDescription,
        modifier = modifier.background(androidx.compose.ui.graphics.Color.White).padding(8.dp).size(size),
    )
}
