# ---- Build stage ----
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
# Copy pom.xml first to leverage Docker caching
COPY pom.xml .
# Download dependencies offline (caching)
RUN mvn dependency:go-offline -B
# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# Copy the built jar from builder
COPY --from=builder /app/target/*.jar app.jar
# Create uploads directory and set permissions
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app
# Switch to non-root user
USER appuser
# Expose the application port (change if your server.port is different)
EXPOSE 8082
# Run the application
# The SPRING_PROFILES_ACTIVE can be set via environment variable (e.g., "docker")
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:default}", "app.jar"]