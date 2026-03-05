package com.baak.astronode.ui.screen.map

import com.baak.astronode.core.model.SkyMeasurement
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

/**
 * SkyMeasurement'ı ClusterManager için ClusterItem olarak sarmalar.
 */
data class MeasurementClusterItem(
    val measurement: SkyMeasurement
) : ClusterItem {

    override fun getPosition(): LatLng =
        LatLng(measurement.latitude, measurement.longitude)

    override fun getTitle(): String =
        "MPSAS: ${measurement.sqmValue}"

    override fun getSnippet(): String =
        "Bortle: ${measurement.bortleClass}"

    override fun getZIndex(): Float = 0f
}
