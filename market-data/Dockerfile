# Build stage
FROM eclipse-temurin:21.0.1_12-jdk-alpine AS build
WORKDIR /app

# Copy the entire repo (wrapper, settings, all modules)
COPY . .

RUN chmod +x ./gradlew

# Build only the market-data subproject
RUN ./gradlew :market-data:bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21.0.1_12-jre-alpine
WORKDIR /app

COPY --from=build /app/market-data/build/libs/*SNAPSHOT*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
