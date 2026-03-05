package com.baak.astronode.core.util

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation

/**
 * GeoHash hesaplama yardımcıları.
 * precision 7 ≈ ~150m hassasiyet (ışık kirliliği ölçümleri için yeterli).
 */
object GeoHashUtil {

    /**
     * Enlem/boylam çiftini GeoHash string'e dönüştürür.
     * @param lat Enlem
     * @param lng Boylam
     * @param precision GeoHash karakter sayısı (varsayılan 7 ≈ ~150m)
     */
    fun encode(lat: Double, lng: Double, precision: Int = 7): String {
        val hash = GeoFireUtils.getGeoHashForLocation(GeoLocation(lat, lng))
        return hash.take(precision).ifEmpty { hash }
    }
}
