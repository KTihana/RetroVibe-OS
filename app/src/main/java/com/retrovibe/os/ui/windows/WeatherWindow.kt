package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

fun weatherCodeToLabel(code: Int?): String = when (code) {
    null -> "—"
    0 -> "Clear"
    1, 2 -> "Partly Cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
    71, 73, 75, 77, 85, 86 -> "Snow"
    95, 96, 99 -> "Thunderstorm"
    else -> "Cloudy"
}

fun weatherCodeToShort(code: Int?): String = when (code) {
    null -> "--"
    0 -> "SUN"
    1, 2 -> "CLD"
    3 -> "OVC"
    45, 48 -> "FOG"
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "RN"
    71, 73, 75, 77, 85, 86 -> "SNW"
    95, 96, 99 -> "STM"
    else -> "CLD"
}

data class DailyForecast(
    val date: String,
    val code: Int,
    val minC: Int,
    val maxC: Int
)

suspend fun fetchWeatherSnapshot(lat: Double, lon: Double): Pair<Pair<Int, Int>, List<DailyForecast>>? {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val root = JSONObject(text)
            val cw = root.getJSONObject("current_weather")
            val temp = cw.getDouble("temperature").roundToInt()
            val code = cw.getInt("weathercode")

            val daily = root.optJSONObject("daily")
            val days = mutableListOf<DailyForecast>()
            if (daily != null) {
                val dates = daily.getJSONArray("time")
                val codes = daily.getJSONArray("weathercode")
                val maxs = daily.getJSONArray("temperature_2m_max")
                val mins = daily.getJSONArray("temperature_2m_min")
                for (i in 0 until minOf(4, dates.length())) {
                    days.add(
                        DailyForecast(
                            date = dates.getString(i),
                            code = codes.getInt(i),
                            minC = mins.getDouble(i).roundToInt(),
                            maxC = maxs.getDouble(i).roundToInt()
                        )
                    )
                }
            }
            (temp to code) to days
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun WeatherWindow(vm: OSViewModel, palette: RetroPalette) {
    val scope = rememberCoroutineScope()
    var cityInput by remember { mutableStateOf(vm.weatherCity) }
    var loading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Tap Refresh or search a city") }
    var forecast by remember { mutableStateOf<List<DailyForecast>>(emptyList()) }

    fun loadFor(lat: Double, lon: Double, cityName: String) {
        loading = true
        statusText = "Fetching $cityName..."
        scope.launch {
            val res = fetchWeatherSnapshot(lat, lon)
            if (res != null) {
                vm.weatherTemp = res.first.first
                vm.weatherCode = res.first.second
                forecast = res.second
                statusText = "Updated for $cityName"
            } else {
                statusText = "Could not load weather (offline?)"
            }
            loading = false
        }
    }

    fun searchCity() {
        val q = cityInput.trim()
        if (q.isEmpty()) return
        loading = true
        statusText = "Searching '$q'..."
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                try {
                    val enc = URLEncoder.encode(q, "UTF-8")
                    val conn = URL("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=1&language=en&format=json")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    conn.disconnect()
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val first = results.getJSONObject(0)
                        Triple(
                            first.getString("name"),
                            first.getDouble("latitude"),
                            first.getDouble("longitude")
                        )
                    } else null
                } catch (_: Throwable) {
                    null
                }
            }
            if (found != null) {
                vm.setWeatherLocation(found.first, found.second, found.third)
                cityInput = found.first
                loadFor(found.second, found.third, found.first)
            } else {
                loading = false
                statusText = "City not found: $q"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFor(vm.weatherLat, vm.weatherLon, vm.weatherCity)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // City Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = cityInput,
                onValueChange = { cityInput = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = palette.textPrimary),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.windowSurface)
                            .border(1.dp, palette.borderDark)
                            .padding(5.dp)
                    ) {
                        if (cityInput.isEmpty()) Text("City name...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            RetroButton("Set", palette, active = true, onClick = { searchCity() })
            RetroButton("R", palette, onClick = { loadFor(vm.weatherLat, vm.weatherLon, vm.weatherCity) })
        }

        // Current Weather Card
        BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.windowSurface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PixelIcon("weather", 42.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vm.weatherCity,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (vm.weatherTemp != null) "${vm.weatherTemp}°C  •  ${weatherCodeToLabel(vm.weatherCode)}" else if (loading) "Loading..." else "—",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.titleBarActive
                    )
                }
            }
        }

        // 4-Day Quiet Forecast
        BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.windowSurface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Forecast",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary
                )
                if (forecast.isEmpty()) {
                    Text(
                        if (loading) "Fetching forecast..." else "No forecast data.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = palette.textSecondary
                    )
                } else {
                    forecast.forEachIndexed { idx, day ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (idx == 0) "Today" else day.date.takeLast(5),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                modifier = Modifier.width(56.dp)
                            )
                            Text(
                                weatherCodeToLabel(day.code),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = palette.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${day.minC}° / ${day.maxC}°",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        }
                        if (idx < forecast.lastIndex) HorizontalDivider(color = palette.borderDark, thickness = 1.dp)
                    }
                }
            }
        }

        // Status
        BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
            Text(
                statusText,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
