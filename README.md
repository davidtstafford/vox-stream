# VoxStream

A Java-based streaming platform integration application that provides Text-to-Speech capabilities, event management, and viewer interaction features.

## Features

- **Multi-Platform Support**: Currently supports Twitch with extensible architecture for additional platforms
- **Text-to-Speech**: AWS Polly integration with local TTS options
- **Event Management**: Real-time event processing with history and search
- **Viewer Management**: Track and manage viewer permissions across platforms  
- **Web Output**: Customizable HTML output for OBS integration
- **System Commands**: Extensible command system for enhanced functionality

## System Requirements

### macOS
- **macOS Catalina (10.15) or later** (64-bit only)
- Apple Developer Certificate required for distribution
- App notarization required for Catalina+

### Windows
- **Windows 10 or later**
- Code signing certificate recommended

### Development
- **Java 17+** (required for macOS Catalina compatibility)
- **Maven 3.6+**
- **JavaFX 17+** (bundled with build)

## Quick Start

### Building the Application

```bash
# Clone the repository
git clone <repository-url>
cd vox-stream

# Build all modules
mvn clean compile

# Run tests
mvn test

# Package application
mvn package
```

### Running the Application

```bash
# Run from frontend module
cd voxstream-frontend
mvn javafx:run

# Or run with Spring Boot
mvn spring-boot:run
```

### Development Mode

```bash
# Start with development profile
mvn javafx:run -Dspring.profiles.active=dev
```

## Project Structure

```
vox-stream/
├── pom.xml                    # Parent POM with dependency management
├── voxstream-core/            # Core models and configuration
├── voxstream-frontend/        # JavaFX GUI application  
├── voxstream-backend/         # Backend services and event processing
├── voxstream-platform-api/    # Streaming platform integrations
├── voxstream-tts/            # Text-to-Speech functionality
└── voxstream-web-output/     # Web output for OBS integration
```

## Development Setup

### Prerequisites

1. **Java 17+**: Download from [Adoptium](https://adoptium.net/)
2. **Maven 3.6+**: Download from [Apache Maven](https://maven.apache.org/)
3. **IDE**: IntelliJ IDEA or VS Code recommended

### Environment Setup

```bash
# Verify Java version (must be 17+)
java -version

# Verify Maven
mvn -version

# Verify 64-bit architecture (required for macOS Catalina)
java -XshowSettings:properties | grep os.arch
```

### IDE Configuration

#### IntelliJ IDEA
1. Import as Maven project
2. Set Project SDK to Java 17+
3. Enable annotation processing
4. Install JavaFX Scene Builder plugin (optional)

#### VS Code
1. Install Java Extension Pack
2. Configure `java.configuration.runtimes` for Java 17+
3. Install Maven for Java extension

### Running Tests

```bash
# Run all tests
mvn test

# Run specific module tests
mvn test -pl voxstream-frontend

# Run with coverage
mvn test jacoco:report
```

## Configuration

### Application Properties

Located in `voxstream-frontend/src/main/resources/application.properties`:

```properties
# Database
voxstream.database.type=H2
voxstream.database.path=./data/voxstream.db

# Event Bus  
voxstream.event-bus.max-events=10000
voxstream.event-bus.purge-interval-minutes=10

# TTS
voxstream.tts.enabled=false
voxstream.tts.provider=AWS_POLLY
voxstream.tts.voice=Joanna

# Web Output
voxstream.web-output.port=8080
voxstream.web-output.host=localhost
```

### Logging

Logs are written to:
- Console output (development)
- `logs/voxstream.log` (production)

Log level can be adjusted in `logback-spring.xml`.

## Building for Distribution

### macOS DMG (Catalina+ Compatible)

```bash
# Build application
mvn clean package

# Create DMG with jpackage (requires Java 17+)
jpackage --type dmg \
  --input target/ \
  --name VoxStream \
  --main-jar voxstream-frontend-1.0.0-SNAPSHOT.jar \
  --main-class com.voxstream.frontend.VoxStreamApplication \
  --app-version 1.0.0 \
  --vendor "VoxStream Project" \
  --copyright "© 2025 VoxStream Project" \
  --mac-package-identifier com.voxstream.app \
  --mac-package-name VoxStream
```

### Windows EXE

```bash
# Build application  
mvn clean package

# Create EXE with jpackage
jpackage --type exe \
  --input target/ \
  --name VoxStream \
  --main-jar voxstream-frontend-1.0.0-SNAPSHOT.jar \
  --main-class com.voxstream.frontend.VoxStreamApplication \
  --app-version 1.0.0 \
  --vendor "VoxStream Project" \
  --copyright "© 2025 VoxStream Project"
```

## macOS Catalina Compatibility Notes

### Critical Requirements
- **64-bit Only**: All components must be 64-bit compatible
- **Java 17+**: Required for full Catalina compatibility  
- **Notarization**: Required for distribution without security warnings
- **Code Signing**: All executables must be signed

### Development Considerations
- Test on actual Catalina systems when possible
- Verify Gatekeeper behavior with signed/unsigned builds
- Ensure all native dependencies are 64-bit and signed
- Use hardened runtime for notarization

## Contributing

1. Create feature branch from `main`
2. Make changes following coding standards
3. Add/update tests as needed
4. Update documentation
5. Submit Pull Request

### Coding Standards
- Java 17 language features
- Spring Framework conventions
- JavaFX best practices  
- Comprehensive error handling
- Logging at appropriate levels

## License

[License information to be added]

## Support

- Issues: [GitHub Issues](repository-issues-url)
- Documentation: [Wiki](repository-wiki-url)
- Discussions: [GitHub Discussions](repository-discussions-url)
