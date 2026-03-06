package com.baak.astronode.ui.screen.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.baak.astronode.core.util.BortleScale
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.view.DefaultClusterRenderer

/**
 * Cluster ve tek noktaları Bortle skalasına göre renklendiren özel renderer.
 * Cluster içinde ortalama Bortle değeri ve ölçüm sayısı gösterilir.
 */
class BortleClusterRenderer(
    context: Context,
    map: com.google.android.gms.maps.GoogleMap,
    clusterManager: com.google.maps.android.clustering.ClusterManager<MeasurementClusterItem>
) : DefaultClusterRenderer<MeasurementClusterItem>(context, map, clusterManager) {

    private val singleItemSize = 48
    private val clusterSize = 64

    /** Test ölçümleri gri renkte gösterilir */
    private val testMeasurementColor = 0xFF9E9E9E.toInt()

    override fun onBeforeClusterItemRendered(
        item: MeasurementClusterItem,
        markerOptions: com.google.android.gms.maps.model.MarkerOptions
    ) {
        val color = if (item.measurement.isTest) {
            testMeasurementColor
        } else {
            BortleScale.toBortleColorInt(item.measurement.bortleClass)
        }
        markerOptions.icon(createCircleBitmapDescriptor(color, singleItemSize))
        markerOptions.title("")  // Varsayılan InfoWindow yazısını kapat (tek noktada Card gösteriyoruz)
        markerOptions.snippet("")
    }

    override fun onBeforeClusterRendered(
        cluster: Cluster<MeasurementClusterItem>,
        markerOptions: com.google.android.gms.maps.model.MarkerOptions
    ) {
        markerOptions.title("")  // Cluster üstünde okunamayan yazı OLMASIN
        markerOptions.snippet("")
        val items = cluster.items
        val avgBortle = if (items.isNotEmpty()) {
            items.map { it.measurement.bortleClass }.average().toInt().coerceIn(1, 9)
        } else 9
        val color = BortleScale.toBortleColorInt(avgBortle)
        markerOptions.icon(createClusterBitmapDescriptor(color, clusterSize, items.size))
    }

    private fun createCircleBitmapDescriptor(color: Int, size: Int): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val strokePaint = Paint().apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = 2f
            this.color = 0x88FFFFFF.toInt()
            isAntiAlias = true
        }
        val rect = RectF(2f, 2f, (size - 2).toFloat(), (size - 2).toFloat())
        canvas.drawOval(rect, paint)
        canvas.drawOval(rect, strokePaint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun createClusterBitmapDescriptor(color: Int, size: Int, count: Int): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val strokePaint = Paint().apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = 3f
            this.color = 0xAAFFFFFF.toInt()
            isAntiAlias = true
        }
        val rect = RectF(4f, 4f, (size - 4).toFloat(), (size - 4).toFloat())
        canvas.drawOval(rect, paint)
        canvas.drawOval(rect, strokePaint)

        if (count > 0) {
            val textPaint = Paint().apply {
                this.color = 0xFFFFFFFF.toInt()
                textSize = size * 0.35f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val text = if (count > 99) "99+" else count.toString()
            val x = size / 2f
            val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, x, y, textPaint)
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
