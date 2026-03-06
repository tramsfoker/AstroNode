package com.baak.astronode.data.api

import android.util.Log
import com.baak.astronode.core.model.WeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedWeather: WeatherData? = null
    private var lastWeatherFetch: Long = 0
    private var lastWeatherLat: Double = 0.0
    private var lastWeatherLng: Double = 0.0
    private val WEATHER_CACHE_MS = 15 * 60 * 1000L // 15 dakika
    private val locEpsilon = 0.02 // ~2km

    suspend fun getWeatherData(lat: Double, lng: Double): WeatherData? {
        val now = System.currentTimeMillis()
        val cacheValid = cachedWeather != null &&
            (now - lastWeatherFetch) < WEATHER_CACHE_MS &&
            kotlin.math.abs(lat - lastWeatherLat) < locEpsilon &&
            kotlin.math.abs(lng - lastWeatherLng) < locEpsilon
        if (cacheValid) return cachedWeather

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lng" +
                    "&current=temperature_2m,relative_humidity_2m," +
                    "cloud_cover,wind_speed_10m,visibility" +
                    "&timezone=auto"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val current = json.optJSONObject("current") ?: return@withContext null

                val result = WeatherData(
                    temperature = current.optDouble("temperature_2m", Double.NaN).takeIf { !it.isNaN() },
                    humidity = current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
                    cloudCover = current.optInt("cloud_cover", -1).takeIf { it >= 0 },
                    windSpeed = current.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() },
                    visibility = current.optDouble("visibility", Double.NaN).takeIf { !it.isNaN() }
                )
                cachedWeather = result
                lastWeatherFetch = System.currentTimeMillis()
                lastWeatherLat = lat
                lastWeatherLng = lng
                result
            } catch (e: Exception) {
                Log.e("WEATHER", "API hatası: ${e.message}")
                cachedWeather // Hata olursa eski cache'i döndür
            }
        }
    }
}
