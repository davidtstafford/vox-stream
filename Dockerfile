# VoxStream Development Environment
# This Dockerfile creates a development environment for VoxStream
# with all necessary tools for building and testing

FROM eclipse-temurin:17-jdk

# Install additional development tools
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /workspace

# Copy Maven wrapper and pom files
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY */pom.xml ./*/

# Download dependencies (this improves build caching)
RUN ./mvnw dependency:resolve dependency:resolve-sources

# Copy source code
COPY . .

# Set default command
CMD ["./mvnw", "compile"]
