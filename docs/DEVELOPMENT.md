# VoxStream Development Guide

## Prerequisites

- **Java 17+** (required for macOS Catalina compatibility)
- **Maven 3.6+** (or use the provided Maven wrapper `./mvnw`)
- **Git**
- **Docker** (optional, for containerized development)

## Project Structure

```
vox-stream/
├── voxstream-parent/          # Parent POM with shared configuration
├── voxstream-core/            # Core models and configuration
├── voxstream-frontend/        # JavaFX GUI application
├── voxstream-backend/         # Backend services (future)
├── voxstream-platform-api/    # Platform integration (Twitch, etc.)
├── voxstream-tts/             # Text-to-Speech engine
├── voxstream-web-output/      # Web output for OBS integration
├── .github/workflows/         # CI/CD configuration
├── .vscode/                   # VS Code settings
├── Dockerfile                 # Development container
└── docker-compose.yml         # Local development setup
```

## Quick Start

### 1. Build the Project

```bash
# Using Maven wrapper (recommended)
./mvnw clean compile

# Or with system Maven
mvn clean compile
```

### 2. Run Tests

```bash
./mvnw test
```

### 3. Run the Application

```bash
cd voxstream-frontend
../mvnw javafx:run
```

### 4. Package the Application

```bash
./mvnw clean package
```

## Development Workflows

### Using Docker (Optional)

```bash
# Build and start development environment
docker-compose up --build

# Run tests in container
docker-compose run voxstream-dev ./mvnw test

# Debug with remote debugging on port 5005
docker-compose up
```

### IDE Setup

#### VS Code
- Install "Extension Pack for Java"
- Install "JavaFX Support"
- Open the workspace folder - settings are pre-configured

#### IntelliJ IDEA
- Open as Maven project
- Ensure Project SDK is set to Java 17+
- Enable Maven auto-import

### Running with Different Profiles

```bash
# Development profile (more verbose logging)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Testing

### Unit Tests
```bash
./mvnw test -Dtest=VoxStreamApplicationTest
```

### Integration Tests (Future)
```bash
./mvnw verify -P integration-tests
```

### macOS Catalina Compatibility Tests
The project includes specific tests for macOS Catalina compatibility:
- 64-bit architecture validation
- Java 17+ version checks
- Security permissions validation (future)

## Debugging

### JavaFX Application
```bash
# Run with debug output
./mvnw javafx:run -Djavafx.verbose=true

# Run headless for testing
./mvnw test -Djava.awt.headless=true
```

### Remote Debugging
```bash
# Start application with debug port
./mvnw javafx:run -Djavafx.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

## Code Style

The project uses:
- Google Java Style Guide (enforced by Maven plugin)
- Automatic import organization
- Format on save (VS Code)

## Contributing

1. Create feature branch from `develop`
2. Make changes and add tests
3. Ensure all tests pass: `./mvnw clean test`
4. Create Pull Request to `develop`
5. After review, changes are merged to `main`

## Troubleshooting

### Common Issues

**JavaFX Not Found**
```bash
# Ensure JavaFX is properly configured
./mvnw javafx:help
```

**Test Failures on macOS**
```bash
# Run tests with proper headless mode
./mvnw test -Djava.awt.headless=true
```

**Build Failures**
```bash
# Clean and rebuild
./mvnw clean compile
```

### macOS Catalina Specific Issues

**Application Won't Start**
- Ensure you're using Java 17+ (64-bit)
- Check System Preferences > Security & Privacy

**Gatekeeper Blocks Execution**
- For development builds, use: `xattr -d com.apple.quarantine voxstream.app`
- For distribution, proper code signing is required

## Phase 1 Completion Status

✅ **Completed:**
- Multi-module Maven project structure
- JavaFX application shell
- Basic configuration system
- Unit testing framework
- Docker development environment
- VS Code integration
- GitHub Actions CI/CD
- macOS Catalina compatibility checks

🔄 **Next Steps (Phase 2):**
- Event bus system implementation
- Database layer setup
- Platform API integration
- TTS engine implementation

For more detailed information, see the main [README.md](../README.md) and [plan.md](../plan.md).
