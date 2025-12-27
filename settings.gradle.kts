rootProject.name = "trading-platform"

include(
    "market-data",
    "trade-processor",
    "websocket-broadcaster",
    "common-models",
    "historical-service"
)

// Optional: nicer project names
project(":market-data").projectDir = file("market-data")
project(":trade-processor").projectDir = file("trade-processor")
project(":websocket-broadcaster").projectDir = file("websocket-broadcaster")
project(":historical-service").projectDir = file("historical-service")