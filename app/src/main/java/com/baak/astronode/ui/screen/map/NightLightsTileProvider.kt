package com.baak.astronode.ui.screen.map

import com.google.android.gms.maps.model.UrlTileProvider
import java.net.URL

/**
 * NASA GIBS "Black Marble" (VIIRS, 2016) gece ışıkları katmanı — kamu malı veri.
 * Katman 0–8 yakınlaştırma seviyelerinde mevcut; daha yakında null döner (Google ölçekler).
 */
class NightLightsTileProvider : UrlTileProvider(TILE_SIZE, TILE_SIZE) {
    override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
        if (zoom > MAX_ZOOM) return null
        return URL("$BASE/$zoom/$y/$x.png")
    }

    companion object {
        private const val TILE_SIZE = 256
        const val MAX_ZOOM = 8
        private const val BASE =
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/VIIRS_Black_Marble/default/2016-01-01/GoogleMapsCompatible_Level8"
    }
}
