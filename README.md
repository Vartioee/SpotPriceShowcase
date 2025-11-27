# ⚡ Finnish Spot Price Dashboard (SpotPriceShowcase)

A modern Android application built entirely with **Jetpack Compose** to fetch, visualize, and present real-time and historical electricity spot prices in Finland from the `spot-hinta.fi` API.

This project demonstrates expertise in Android UI, state management using Unidirectional Data Flow (UDF), asynchronous programming with Coroutines, and custom data visualization.

## ✨ Key Features

* **Real-time Data Fetching:** Retrieves current, today's, and tomorrow's hourly electricity spot prices.
* **Historical Data:** Fetches and displays price history for the last 7 days.
* **Custom UI:** Features a interactive **3D Rotating Card Pager** effect using `HorizontalPager` and `graphicsLayer` transformations.
* **Custom Data Visualization:** Includes a **Line Graph Composable** (`PriceGraph`) built with the `Canvas` API for price visualization.
* **State Management:** Implements robust **Unidirectional Data Flow (UDF)** using a `sealed class` (`DataState`) for clear handling of `Loading`, `Success`, and `Error` states.

## 🛠️ Tech Stack & Architecture

| Category | Technology/Concept | Purpose |
| :--- | :--- | :--- |
| **UI Framework** | **Jetpack Compose** | Declarative, modern Android UI toolkit. |
| **Concurrency** | **Kotlin Coroutines** (`Dispatchers.IO`, `suspend`) | Handles network operations off the main thread for performance. |
| **Architecture** | **Unidirectional Data Flow (UDF)** | Clear, predictable data flow (`DataState` sealed class). |
| **Networking** | **`HttpURLConnection`** | Used for raw, efficient API requests to `https://api.spot-hinta.fi`. |
| **Visualization** | **`Canvas` API** | Custom composable for drawing the price line graph. |
| **API** | **`spot-hinta.fi`** | Public API for electricity market price data. |

## 🚀 Getting Started

### Prerequisites

* Android Studio Jellyfish | 2023.3.1 or newer
* Kotlin 1.9+
* Android SDK 34 (Target SDK 34)

### Running the Project

1.  **Clone the Repository:**
    ```bash
    git clone [https://github.com/YourUsername/spot-price-showcase.git](https://github.com/YourUsername/spot-price-showcase.git)
    ```
2.  **Open in Android Studio:** Open the cloned directory as an Android Studio project.
3.  **Run:** Select a physical device (requires **USB Debugging** enabled) or an Android Emulator and click the **Run** button (▶️).
