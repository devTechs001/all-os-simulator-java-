# Multi-stage build for optimal size
FROM maven:3.8-openjdk-17 as builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:17-jdk-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    nodejs \
    npm \
    ffmpeg \
    libopencv-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Python ML libraries
RUN pip3 install \
    tensorflow \
    torch \
    opencv-python \
    numpy \
    scikit-learn \
    pandas

WORKDIR /app

# Copy built JAR
COPY --from=builder /app/target/all-os-simulator-2.0.0.jar app.jar

# Copy AI models
COPY ai-models /app/ai-models

# Copy configuration
COPY config /app/config

# Expose ports
EXPOSE 8080 8081 5000 9090

# Set environment variables
ENV JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"
ENV AI_MODEL_PATH=/app/ai-models
ENV CONFIG_PATH=/app/config

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]