# pro-portfolio

Proje yapısı

PortfolioApp/
│
├── .github/
│   └── workflows/
│       └── android.yml
│
├── app/
│   ├── build.gradle.kts
│   │
│   └── src/main/
│       ├── AndroidManifest.xml
│       │
│       ├── java/com/example/portfolio/
│       │   ├── MainActivity.kt
│       │   │
│       │   ├── database/
│       │   │   ├── PortfolioDatabase.kt
|       |   |   └── PortfolioRepository.kt  
│       │   │
│       │   ├── analytics/
|       |   |   ├── BenchmarkAnalyzer.kt
|       |   |   └── TechnicalIndicators.kt
|       |   |      
│       │   ├── model/
|       |   |   ├── Models.kt
|       |   |   ├── Candle.kt
│       │   │   └── PortfolioAsset.kt
│       │   │
│       │   ├── network/
|       |   |   ├── MarketDataRepository.kt
│       │   │   └── PriceRepository.kt
|       |   | 
│       │   ├── reports/
│       │   │   └── PortfolioExporter.kt
|       |   |  
│       │   ├── alerts/
│       │   │   └── PriceAlertWorker.kt
│       │   │
│       │   └── ui/
│       │       ├── PortfolioChart.kt
|       |       ├── CandlestickChart.kt
|       |       ├── IndicatorChart.kt
|       |       └── PerformanceChart.kt
│       │
│       ├── res/
│       │   ├── drawable/
│       │   ├── mipmap-hdpi/
│       │   ├── mipmap-mdpi/
│       │   ├── mipmap-xhdpi/
│       │   ├── mipmap-xxhdpi/
│       │   ├── mipmap-xxxhdpi/
│       │   └── values/
│       │       ├── colors.xml
│       │       └── styles.xml
│       │
│       └── assets/
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
