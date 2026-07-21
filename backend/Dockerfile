# Stage 1: Build the application
FROM maven:3.9-amazoncorretto-17 AS builder
WORKDIR /app
COPY pom.xml .

# Download dependencies first to cache them
RUN mvn dependency:resolve -B
# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the minimal runtime image
FROM amazoncorretto:17-alpine
WORKDIR /app

# Add a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built jar from the builder stage
COPY --from=builder /app/target/gateway-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
