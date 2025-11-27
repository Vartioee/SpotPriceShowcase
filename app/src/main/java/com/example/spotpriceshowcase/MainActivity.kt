package com.example.spotpriceshowcase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

// --- 1. Data Model matching spot-hinta.fi API response ---

/**
 * Data class representing a single hourly price entry from the API.
 */
data class PriceEntry(
    val datetime: String, // e.g., "2023-10-27T00:00:00+03:00"
    val priceEurPerMWh: Double // The spot price in cents per kWh (API returns this)
) {
    // Helper property to parse the datetime string into a ZonedDateTime object
    val parsedDateTime: ZonedDateTime?
        get() = try {
            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            ZonedDateTime.parse(datetime, formatter)
        } catch (e: Exception) {
            null
        }

    // Property to reliably get the hour (0-23)
    val hour: Int
        get() = parsedDateTime?.hour ?: 0

    // Property to reliably get the full date and time for display
    val displayTime: String
        get() = parsedDateTime?.format(DateTimeFormatter.ofPattern("EEEE, HH:mm", Locale.getDefault())).orEmpty()

    // Property to get the day of the year for comparison
    val dayOfYear: Int
        get() = parsedDateTime?.dayOfYear ?: -1
}

// --- 2. State Management for Data Fetching ---

sealed class DataState {
    object Loading : DataState()
    data class Success(val prices: List<PriceEntry>) : DataState()
    data class Error(val message: String) : DataState()
}

// --- 3. Real API Implementation ---

/**
 * Fetches today's and tomorrow's prices from spot-hinta.fi API
 * API returns prices in cents/kWh for today and tomorrow
 */
suspend fun fetchTodayAndTomorrowPrices(): List<PriceEntry> = withContext(Dispatchers.IO) {
    val apiUrl = "https://api.spot-hinta.fi/TodayAndDayForward"

    val connection = URL(apiUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10000
    connection.readTimeout = 10000

    try {
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parsePriceData(response)
        } else {
            throw Exception("HTTP Error: $responseCode")
        }
    } finally {
        connection.disconnect()
    }
}

/**
 * Parses the JSON response from the API
 * Expected format: Array of objects with "DateTime" and "PriceWithTax" fields
 */
private fun parsePriceData(jsonResponse: String): List<PriceEntry> {
    val prices = mutableListOf<PriceEntry>()
    val jsonArray = JSONArray(jsonResponse)

    for (i in 0 until jsonArray.length()) {
        val item = jsonArray.getJSONObject(i)
        val dateTime = item.getString("DateTime")
        // API returns price in cents/kWh, convert to €/MWh by multiplying by 10
        val priceWithTax = item.getDouble("PriceWithTax") * 10.0

        prices.add(PriceEntry(
            datetime = dateTime,
            priceEurPerMWh = priceWithTax
        ))
    }

    return prices
}

/**
 * Fetches 7 days of historical data
 * Note: spot-hinta.fi API might have limited historical data
 * For a complete 7-day history, you may need to call the API multiple times
 * or use a different endpoint if available
 */
suspend fun fetchWeeklyPrices(): List<PriceEntry> = withContext(Dispatchers.IO) {
    // For now, we'll use the same endpoint as it provides today and tomorrow
    // In a production app, you'd need to implement proper historical data fetching
    // possibly by calling the API with date parameters or using a different endpoint

    val apiUrl = "https://api.spot-hinta.fi/TodayAndDayForward"

    val connection = URL(apiUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10000
    connection.readTimeout = 10000

    try {
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parsePriceData(response)
        } else {
            throw Exception("HTTP Error: $responseCode")
        }
    } finally {
        connection.disconnect()
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                RotatingCircularPager()
            }
        }
    }
}

// --- 4. Theme Definition (Dark Mode) ---

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF4DD0E1),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF00BCD4),
            secondary = Color(0xFF009688),
            background = Color.White,
            surface = Color(0xFFF5F5F5),
            onPrimary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// --- 5. Graph Drawing Composable (Uses PriceEntry) ---

@Composable
fun PriceGraph(hourlyPrices: List<PriceEntry>, lineColor: Color, isWeekly: Boolean = false) {
    val prices = hourlyPrices.map { it.priceEurPerMWh }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 1.0

    // Ensure range is not zero if all prices are the same
    val range = if (maxPrice - minPrice == 0.0) 1.0 else (maxPrice - minPrice) * 1.1

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        val width = size.width
        val height = size.height
        val totalHours = hourlyPrices.size.toFloat()
        val stepX = if (totalHours > 1) width / (totalHours - 1) else 0f

        val path = Path()

        // Sort data by datetime to ensure the graph draws chronologically
        hourlyPrices.sortedBy { it.datetime }.forEachIndexed { index, data ->
            val xCoord = index * stepX

            // Normalize Y coordinate (price value scaled to height)
            val normalizedY = ((data.priceEurPerMWh - minPrice) / range).toFloat()
            val yCoord = height - (normalizedY * height)

            if (index == 0) {
                path.moveTo(xCoord, yCoord)
            } else {
                path.lineTo(xCoord, yCoord)
            }

            // Draw data point circle only for daily view
            if (!isWeekly) {
                drawCircle(
                    color = lineColor,
                    radius = 8f,
                    center = Offset(xCoord, yCoord)
                )
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 6f)
        )
    }
}


// --- 6. Main Pager Composable (Handles Pager and State) ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RotatingCircularPager() {
    // Separate state for today's data (used on Page 0)
    var todayDataState by remember { mutableStateOf<DataState>(DataState.Loading) }
    // Separate state for weekly history (used on Page 1)
    var weeklyDataState by remember { mutableStateOf<DataState>(DataState.Loading) }

    // Fetch data for today and tomorrow
    LaunchedEffect(Unit) {
        try {
            todayDataState = DataState.Success(fetchTodayAndTomorrowPrices())
        } catch (e: Exception) {
            todayDataState = DataState.Error("Failed to load data: ${e.message}")
        }
    }

    // Fetch data for weekly view
    LaunchedEffect(Unit) {
        try {
            weeklyDataState = DataState.Success(fetchWeeklyPrices())
        } catch (e: Exception) {
            weeklyDataState = DataState.Error("Failed to load weekly data: ${e.message}")
        }
    }

    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.ElectricBolt,
                contentDescription = "Electricity Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Finnish Spot Price Dashboard",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { it }
            ) { pageIndex ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.8f)
                        .padding(16.dp)
                        .graphicsLayer {
                            val pageOffset = (
                                    (pagerState.currentPage - pageIndex) +
                                            pagerState.currentPageOffsetFraction
                                    ).absoluteValue

                            val scale = lerp(
                                start = 0.85f,
                                stop = 1f,
                                fraction = 1f - pageOffset.coerceIn(0f, 1f)
                            )
                            var rotationY = lerp(
                                start = 0f,
                                stop = 90f,
                                fraction = pageOffset.coerceIn(0f, 1f)
                            ) * if (pagerState.currentPage < pageIndex) 1 else -1

                            scaleX = scale
                            scaleY = scale
                            rotationY = rotationY
                            cameraDistance = 12 * density
                        }
                ) {
                    val dataForPage = when (pageIndex) {
                        0 -> todayDataState
                        1 -> weeklyDataState
                        else -> todayDataState
                    }
                    when (dataForPage) {
                        is DataState.Loading -> LoadingScreen()
                        is DataState.Error -> ErrorScreen((dataForPage as DataState.Error).message)
                        is DataState.Success -> {
                            DataScreen(pageIndex, (dataForPage as DataState.Success).prices)
                        }
                    }
                }
            }

            // Simple indicator
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pageCount) { index ->
                    val color = if (pagerState.currentPage == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(10.dp)
                            .background(color, RoundedCornerShape(50))
                    )
                }
            }
        }
    }
}

// --- 7. State-Specific Screens ---

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Fetching price data...", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ErrorScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Data Error",
            color = MaterialTheme.colorScheme.error,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DataScreen(pageIndex: Int, prices: List<PriceEntry>) {
    val title = when (pageIndex) {
        0 -> "Today & Tomorrow (€/MWh)"
        1 -> "Price History"
        2 -> "Forecast View"
        else -> "Data View"
    }

    // --- Current Price/Time Logic ---
    val currentDateTime = ZonedDateTime.now()
    val currentPriceEntry = prices.firstOrNull {
        it.parsedDateTime?.hour == currentDateTime.hour &&
                it.parsedDateTime?.dayOfYear == currentDateTime.dayOfYear
    }

    // Find the cheapest and most expensive prices
    val minPrice = prices.minOfOrNull { it.priceEurPerMWh }
    val maxPrice = prices.maxOfOrNull { it.priceEurPerMWh }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Quick Stats
        if (pageIndex == 0 || pageIndex == 2) {
            if (currentPriceEntry != null) {
                Text(
                    text = "Current: €${String.format(Locale.getDefault(), "%.2f", currentPriceEntry.priceEurPerMWh)}/MWh",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = currentPriceEntry.displayTime,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (minPrice != null && maxPrice != null) {
                Text(
                    text = "Range: €${String.format(Locale.getDefault(), "%.2f", minPrice)} - €${String.format(Locale.getDefault(), "%.2f", maxPrice)}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        } else if (pageIndex == 1) {
            if (minPrice != null && maxPrice != null) {
                Text(
                    text = "Range: €${String.format(Locale.getDefault(), "%.2f", minPrice)} - €${String.format(Locale.getDefault(), "%.2f", maxPrice)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        ) {
            PriceGraph(
                hourlyPrices = prices,
                lineColor = MaterialTheme.colorScheme.primary,
                isWeekly = pageIndex == 1
            )
        }

        Text(
            text = when(pageIndex) {
                0 -> "Showing ${prices.size} hourly prices from spot-hinta.fi API"
                1 -> "Historical price data"
                else -> "Forecast data"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}


// --- Preview for Dark Mode ---

@Preview(showBackground = true, name = "Dark Mode Preview")
@Composable
fun RotatingCircularPagerPreviewDark() {
    AppTheme(darkTheme = true) {
        RotatingCircularPager()
    }
}