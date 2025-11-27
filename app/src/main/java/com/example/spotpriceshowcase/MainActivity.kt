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
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

// --- 1. Data Model matching sahkohinta-api.fi response ---

/**
 * Data class representing a single hourly price entry from the API.
 */
data class PriceEntry(
    val datetime: String, // e.g., "2023-10-27T00:00:00+03:00"
    val priceEurPerMWh: Double // The spot price
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
        get() = parsedDateTime?.format(DateTimeFormatter.ofPattern("EEEE, HH:mm")).orEmpty()

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

// --- 3. Mock Services (Split for Today and Weekly data) ---

/**
 * **MOCK IMPLEMENTATION:** Simulates fetching 24 hours of data for TODAY.
 * API URL Construction Hint:
 * API expects dates like YYYY/MM/DD. Use LocalDate.now().
 * E.g.: `.../api/v1/price/day/${LocalDate.now().year}/${LocalDate.now().monthValue}/${LocalDate.now().dayOfMonth}`
 */
suspend fun fetchTodaysPrices(): List<PriceEntry> {
    delay(2000)
    // Random error simulation
    if (Math.random() < 0.05) throw Exception("Simulated Daily Data Network Error")

    // Generate 24 points for today
    val today = LocalDate.now()
    return (0..23).map { hour ->
        val basePrice = 30.0 + (kotlin.math.sin(hour / 4.0) * 20.0)
        val noise = Math.random() * 5.0 - 2.5
        PriceEntry(
            datetime = "${today}T${String.format(Locale.getDefault(), "%02d", hour)}:00:00+03:00",
            priceEurPerMWh = String.format(Locale.getDefault(), "%.2f", (basePrice + noise)).toDouble()
        )
    }
}

/**
 * **MOCK IMPLEMENTATION:** Simulates fetching 168 hours of data for the PAST WEEK.
 * API URL Construction Hint:
 * You would loop through the last 7 days and call the daily endpoint 7 times,
 * or use a separate weekly/monthly endpoint if the API provides one.
 */
suspend fun fetchWeeklyPrices(): List<PriceEntry> {
    delay(3000) // Longer delay for more data
    if (Math.random() < 0.05) throw Exception("Simulated Weekly Data Network Error")

    val prices = mutableListOf<PriceEntry>()
    val today = LocalDate.now()

    // Simulate 7 days (168 hours) of data
    for (day in 0 until 7) {
        val date = today.minusDays(day.toLong())
        for (hour in 0..23) {
            val basePrice = 40.0 + (kotlin.math.sin((hour + day * 4) / 8.0) * 30.0)
            val noise = Math.random() * 10.0 - 5.0
            prices.add(
                PriceEntry(
                    datetime = "${date}T${String.format(Locale.getDefault(), "%02d", hour)}:00:00+03:00",
                    priceEurPerMWh = String.format(Locale.getDefault(), "%.2f", (basePrice + noise)).toDouble()
                )
            )
        }
    }
    return prices
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
        // Ensure totalHours is not zero before dividing. For 168 hours, stepX will be much smaller.
        val stepX = if (totalHours > 1) width / (totalHours - 1) else 0f

        val path = Path()

        // Sort data by datetime to ensure the graph draws chronologically, especially for weekly data
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

            // Draw data point circle only for daily view to keep weekly view cleaner
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

    // Fetch data for today
    LaunchedEffect(Unit) {
        try {
            todayDataState = DataState.Success(fetchTodaysPrices())
        } catch (e: Exception) {
            todayDataState = DataState.Error("Daily Data: ${e.message}")
        }
    }

    // Fetch data for the past week
    LaunchedEffect(Unit) {
        try {
            weeklyDataState = DataState.Success(fetchWeeklyPrices())
        } catch (e: Exception) {
            weeklyDataState = DataState.Error("Weekly Data: ${e.message}")
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
                // Custom Page Transform for the Circular Rotation Effect (Unchanged)
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
                        else -> todayDataState // Page 2 can use today's data for forecast mock
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

            // Simple indicator (Unchanged)
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
        0 -> "Today's Hourly Prices (€/MWh)"
        1 -> "Past Week History (168 Hours)"
        2 -> "Hourly Forecast (Mock)"
        else -> "Data View"
    }

    // --- Current Price/Time Logic ---
    val currentDateTime = ZonedDateTime.now()
    // Find the price entry that matches the current day and hour (essential for weekly data)
    val currentPriceEntry = prices.firstOrNull {
        it.parsedDateTime?.hour == currentDateTime.hour &&
                it.parsedDateTime?.dayOfYear == currentDateTime.dayOfYear // Ensures it's today's price
    }

    // Find the cheapest and most expensive prices in the current dataset
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

        // Quick Stats - Show only on the Today/Forecast page for clarity
        if (pageIndex == 0 || pageIndex == 2) {
            if (currentPriceEntry != null) {
                // Now showing the current price, hour, AND day
                Text(
                    text = "Current Price (${currentPriceEntry.displayTime}): €${String.format(Locale.getDefault(), "%.2f", currentPriceEntry.priceEurPerMWh)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (minPrice != null && maxPrice != null) {
                Text(
                    text = "Daily Range: €${String.format(Locale.getDefault(), "%.2f", minPrice)} to €${String.format(Locale.getDefault(), "%.2f", maxPrice)}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        } else if (pageIndex == 1) {
            // Stats for Weekly Page
            if (minPrice != null && maxPrice != null) {
                Text(
                    text = "7-Day Range: €${String.format(Locale.getDefault(), "%.2f", minPrice)} to €${String.format(Locale.getDefault(), "%.2f", maxPrice)}",
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
                0 -> "Graph shows 24 hourly prices for today."
                1 -> "Graph shows 168 hourly prices for the past week."
                else -> "Graph shows simulated forecast data."
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