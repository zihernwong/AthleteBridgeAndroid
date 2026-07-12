package com.athletebridge.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.athletebridge.android.data.model.PlaceToPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Keyless static map snapshot built from OpenStreetMap tiles (the app already
 * uses OSM via osmdroid for the discovery map). Geocodes the address with the
 * platform Geocoder, composites the surrounding tiles into one bitmap and draws
 * a marker at the location. Results are cached in memory per address+size.
 */
private object OsmSnapshotLoader {
    private const val ZOOM = 16
    private const val TILE = 256
    private val bitmapCache = LruCache<String, Bitmap>(20)
    private val coordinateCache = HashMap<String, Pair<Double, Double>>()
    private val failedAddresses = HashSet<String>()

    suspend fun load(context: Context, address: String, widthPx: Int, heightPx: Int): Bitmap? {
        val key = "$address|${widthPx}x$heightPx"
        bitmapCache.get(key)?.let { return it }
        val coords = geocode(context, address) ?: return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { compose(coords.first, coords.second, widthPx, heightPx) }.getOrNull()
        } ?: return null
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    private suspend fun geocode(context: Context, address: String): Pair<Double, Double>? {
        synchronized(coordinateCache) {
            coordinateCache[address]?.let { return it }
            if (failedAddresses.contains(address)) return null
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context).getFromLocationName(address, 1)?.firstOrNull()
            }.getOrNull()
        }
        synchronized(coordinateCache) {
            return if (result != null) {
                val pair = result.latitude to result.longitude
                coordinateCache[address] = pair
                pair
            } else {
                failedAddresses.add(address)
                null
            }
        }
    }

    /** Fetch the tiles covering a widthPx x heightPx viewport centered on the point. */
    private fun compose(lat: Double, lon: Double, widthPx: Int, heightPx: Int): Bitmap? {
        val n = 1 shl ZOOM
        val xTile = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val yTile = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n

        // Global pixel coordinates of the viewport
        val centerPx = xTile * TILE
        val centerPy = yTile * TILE
        val left = centerPx - widthPx / 2.0
        val top = centerPy - heightPx / 2.0

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)

        val firstTileX = floor(left / TILE).toInt()
        val firstTileY = floor(top / TILE).toInt()
        val lastTileX = floor((left + widthPx) / TILE).toInt()
        val lastTileY = floor((top + heightPx) / TILE).toInt()

        var drewAnything = false
        for (tx in firstTileX..lastTileX) {
            for (ty in firstTileY..lastTileY) {
                if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue
                val tile = fetchTile(tx, ty) ?: continue
                canvas.drawBitmap(
                    tile,
                    (tx * TILE - left).toFloat(),
                    (ty * TILE - top).toFloat(),
                    null
                )
                drewAnything = true
            }
        }
        if (!drewAnything) return null

        // Marker at the center
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(214, 45, 32)
        canvas.drawCircle(cx, cy - 10f, 10f, paint)
        // Simple pin tail
        val path = android.graphics.Path().apply {
            moveTo(cx - 7f, cy - 6f)
            lineTo(cx + 7f, cy - 6f)
            lineTo(cx, cy + 8f)
            close()
        }
        canvas.drawPath(path, paint)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx, cy - 10f, 4f, inner)

        return bitmap
    }

    private fun fetchTile(x: Int, y: Int): Bitmap? {
        return runCatching {
            val url = URL("https://tile.openstreetmap.org/$ZOOM/$x/$y.png")
            val conn = url.openConnection() as HttpURLConnection
            // OSM tile policy requires an identifying user agent
            conn.setRequestProperty("User-Agent", "AthleteBridge-Android")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}

/** Static OpenStreetMap snapshot for an address, with a marker at the location. */
@Composable
fun OsmMapSnapshot(address: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = address) {
        if (address.isNotBlank()) {
            value = OsmSnapshotLoader.load(context, address, 640, 360)?.asImageBitmap()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bm = bitmap
        if (bm != null) {
            Image(
                bitmap = bm,
                contentDescription = "Map of $address",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Picture for a Place to Play: the club's uploaded photo when one exists,
 * otherwise a static OpenStreetMap snapshot of its address.
 */
@Composable
fun PlaceImage(place: PlaceToPlay, modifier: Modifier = Modifier) {
    if (!place.photoURL.isNullOrBlank()) {
        AsyncImage(
            model = place.photoURL,
            contentDescription = "Photo of ${place.name}",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else {
        OsmMapSnapshot(address = place.address, modifier = modifier)
    }
}
