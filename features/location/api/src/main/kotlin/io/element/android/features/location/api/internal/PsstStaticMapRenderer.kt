/*
 * PSST (D97): static map thumbnails rendered on the device with MapLibre's snapshotter from the psst map styles,
 * instead of MapTiler's static-maps API (psst has no MapTiler key and must not send locations to a third party).
 */
package io.element.android.features.location.api.internal

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import io.element.android.features.location.api.Location
import io.element.android.features.location.api.PsstMaps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapSnapshotter
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

internal object PsstStaticMapRenderer {
    private data class Key(
        val lat: Double,
        val lon: Double,
        val zoom: Double,
        val darkMode: Boolean,
        val size: DpSize,
        val density: Float,
    )

    // A few dozen thumbnails; scrolling back through the timeline must not render them again.
    private val cache = LruCache<Key, ImageBitmap>(32)

    // One snapshotter per style, created on first use; captures run one after the other.
    private val snapshotters = mutableMapOf<Boolean, MapSnapshotter>()
    private val mutex = Mutex()

    fun cached(location: Location, zoom: Double, darkMode: Boolean, size: DpSize, density: Density): ImageBitmap? =
        cache.get(key(location, zoom, darkMode, size, density))

    /** Must be called from the main thread (MapLibre's runtime). Throws when the map cannot be rendered. */
    suspend fun render(location: Location, zoom: Double, darkMode: Boolean, size: DpSize, density: Density): ImageBitmap {
        val key = key(location, zoom, darkMode, size, density)
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            val snapshotter = snapshotters.getOrPut(darkMode) {
                DefaultMapRuntime.instance.createSnapshotter(BaseStyle.Uri(PsstMaps.styleUrl(darkMode)))
            }
            val image = snapshotter.capture(size) {
                cameraPosition = CameraPosition(
                    center = Position(longitude = location.lon, latitude = location.lat),
                    zoom = zoom,
                )
                this.density = density
            }
            cache.put(key, image)
            image
        }
    }

    private fun key(location: Location, zoom: Double, darkMode: Boolean, size: DpSize, density: Density) =
        Key(location.lat, location.lon, zoom, darkMode, size, density.density)
}
