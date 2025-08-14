# VoxStream - Comprehensive Development Plan

## Project Overview
VoxStream is a Java-based application with a Java frontend and backend that integrates with streaming platforms (starting with Twitch), provides Text-to-Speech capabilities, event management, and viewer interaction features.

### Target Platforms
- **macOS Catalina (10.15) and up** - Released October 2019
  - 64-bit applications ONLY (no 32-bit support)
  - Apple notarization required for distribution
  - Enhanced Gatekeeper security restrictions
- **Windows 10 and up**
- Development using Docker/Dev containers (optional, basic setup)

### CI/CD Requirements
- GitHub-based CI/CD
- Feature branch workflow with Pull Requests
- Automated builds for DMG (macOS) and EXE (Windows)
- **Apple notarization** for macOS builds (required for Catalina+)
- **Code signing** for both platforms
- Version management and validation

---

## Architecture Overview

### Core Components
1. **Frontend**: Java-based GUI (JavaFX recommended)
2. **Backend**: Java services and event processing
3. **Event Bus**: Central event management system
4. **TTS Engine**: Text-to-Speech processing with AWS Polly integration
5. **Database Layer**: Local storage for events, viewers, and configuration
6. **API Layer**: Integration with streaming platforms
7. **Web Output**: HTML pages for OBS integration

---

## Phase 1: Project Foundation & Setup
**Goal**: Establish the basic project structure, build system, and development environment

### 1.1 Project Structure & Build System
- [x] Create Maven multi-module project structure
- [x] Setup parent POM with dependency management
- [x] **Configure Java 17+ for Catalina compatibility (64-bit only)**
- [x] Configure JavaFX module for frontend
- [x] Configure backend service modules
- [x] Setup logging framework (SLF4J + Logback)
- [x] Configure testing framework (JUnit 5)
- [x] Create basic application launcher
- [x] **Ensure all dependencies are 64-bit compatible**

### 1.2 Development Environment
- [x] Create Dockerfile for development environment
- [x] Setup docker-compose for local development
- [x] Configure VS Code/IntelliJ settings
- [x] Create development documentation

### 1.3 CI/CD Pipeline
- [x] Setup GitHub Actions workflow
- [x] Configure build pipeline for multi-platform
- [x] Setup automated testing
- [ ] **Configure Apple Developer Certificate for code signing**
- [ ] **Setup Apple notarization process (required for Catalina)**
- [ ] Configure DMG generation for macOS
- [ ] Configure EXE generation for Windows
- [ ] Implement version validation checks
- [ ] Setup artifact storage and release management
- [ ] **Test distribution on macOS Catalina specifically**

### 1.4 Basic Application Shell
- [x] Create main application class
- [x] Setup JavaFX application structure
- [x] Create basic navigation framework
- [x] Implement application configuration system
- [x] Create basic error handling and logging

**Testing Phase 1:**
- [x] Verify build system works on both macOS Catalina+ and Windows
- [x] **Test 64-bit compatibility on macOS Catalina**
- [ ] **Verify Gatekeeper security compliance**
- [x] Test CI/CD pipeline with sample commits
- [x] Confirm application launches successfully on Catalina
- [x] Validate development environment setup
- [ ] **Test Apple notarization process**

---

## Phase 2: Core Infrastructure
**Goal**: Build the fundamental systems that other features will depend on

### 2.1 Event Bus System
- [x] Design event bus architecture
- [x] Implement core event bus with pub/sub pattern (in-memory skeleton)
- [x] Create event models for different platforms (base/general models added)
- [x] Implement event filtering and routing (basic predicate + priority dispatch)
- [x] Add event persistence to local database (stub service only – pending DB layer in 2.2)
- [x] Implement event cleanup/purging (scheduled purge + max size, expiration support)
- [x] Add event bus monitoring and metrics (basic counters + snapshot)

> NOTE: Persistence wiring completed once DB layer added; advanced querying & replay features will arrive with history system in Phase 5.

### 2.2 Database Layer
- [x] Setup embedded database (H2 or SQLite)
- [x] Create database schema for events
- [ ] Create database schema for viewers
- [ ] Create database schema for configuration
- [ ] Implement DAO pattern for data access
- [x] Add database migration system
- [ ] Implement data backup and restore

### 2.3 Configuration Management
- [ ] Create configuration model classes
- [ ] Implement configuration persistence
- [ ] Create configuration validation
- [ ] Add configuration migration support
- [ ] Implement secure credential storage

### 2.4 Advanced Settings Framework
- [ ] Create settings UI framework
- [ ] Implement event purge time settings
- [ ] Add TTS bus purge time settings
- [ ] Create settings import/export
- [ ] Add settings validation and defaults

**Testing Phase 2:**
- [ ] Test event bus performance with high volume
- [ ] Verify event cleanup works correctly
- [ ] Test database operations and migrations
- [ ] Validate configuration persistence
- [ ] Test settings UI functionality

---

## Phase 3: Streaming Platform Integration
**Goal**: Connect to streaming platforms and capture events

### 3.1 Platform Connection Framework
- [ ] Design platform abstraction layer
- [ ] Create platform configuration models
- [ ] Implement connection management system
- [ ] Add auto-reconnection logic
- [ ] Create connection status monitoring

### 3.2 Twitch Integration (MVP)
- [ ] Setup Twitch API client
- [ ] Implement OAuth authentication flow
- [ ] Connect to Twitch EventSub or IRC
- [ ] Map Twitch events to internal event models
- [ ] Handle Twitch-specific event types:
  - [ ] Viewer joins/leaves
  - [ ] Subscriptions
  - [ ] Subscription renewals  
  - [ ] Bit donations
  - [ ] Chat messages
  - [ ] Follows
  - [ ] Raids
  - [ ] Host events
  - [ ] Channel point redemptions

### 3.3 Connection Management UI
- [ ] Create connection configuration screen
- [ ] Add platform selection interface
- [ ] Implement credential input forms
- [ ] Add connection status indicators
- [ ] Create connection testing functionality
- [ ] Implement sign-out functionality

### 3.4 Platform Abstraction for Future Expansion
- [ ] Design interface for additional platforms
- [ ] Create plugin architecture for new platforms
- [ ] Document platform integration guide

**Testing Phase 3:**
- [ ] Test Twitch connection with real account
- [ ] Verify all event types are captured correctly
- [ ] Test connection recovery and auto-reconnect
- [ ] Validate connection UI functionality
- [ ] Test with multiple concurrent connections

---

## Phase 4: Text-to-Speech System
**Goal**: Implement comprehensive TTS functionality

### 4.1 TTS Engine Integration
- [ ] Research local TTS options (Java Speech API, espeak, etc.)
- [ ] Implement AWS Polly integration
- [ ] Create TTS provider abstraction
- [ ] Add voice selection and management
- [ ] Implement TTS queue management

### 4.2 TTS Processing Pipeline
- [ ] Create TTS bus system
- [ ] Implement event-to-TTS filtering
- [ ] Add text preprocessing and cleaning:
  - [ ] Repeated character limiting
  - [ ] Emoji repetition limiting  
  - [ ] Repeated word limiting
  - [ ] URL and mention handling
- [ ] Create TTS job queuing system
- [ ] Implement audio file generation and cleanup

### 4.3 TTS Admin Interface
- [ ] Create TTS configuration screen
- [ ] Add AWS Polly credential management
- [ ] Implement TTS enable/disable toggle
- [ ] Add voice selection interface
- [ ] Create TTS test functionality
- [ ] Add event selection for TTS reading
- [ ] Implement "viewer asks" vs "just reading" toggle
- [ ] Add text filtering configuration

### 4.4 TTS Web Output
- [ ] Create HTML page for TTS audio output
- [ ] Implement audio streaming to browser
- [ ] Add OBS integration documentation
- [ ] Create customizable audio player interface

**Testing Phase 4:**
- [ ] Test AWS Polly integration
- [ ] Verify local TTS functionality (if implemented)
- [ ] Test text filtering and preprocessing
- [ ] Validate TTS queue management
- [ ] Test web output integration with OBS

---

## Phase 5: Event Management & History
**Goal**: Provide comprehensive event tracking and history

### 5.1 Event History System
- [ ] Extend database schema for long-term storage
- [ ] Implement event archiving system
- [ ] Create event search functionality
- [ ] Add filtering by:
  - [ ] Text content
  - [ ] Viewer name
  - [ ] Event type
  - [ ] Date range
  - [ ] Platform

### 5.2 Event History UI
- [ ] Create event history viewer screen
- [ ] Implement search and filter interface
- [ ] Add pagination for large datasets
- [ ] Create event detail view
- [ ] Add event export functionality
- [ ] Implement selective event deletion
- [ ] Add bulk operations (purge by date, etc.)

### 5.3 Live Event Monitor
- [ ] Create real-time event display screen
- [ ] Add customizable event filters
- [ ] Implement custom alerts for specific events
- [ ] Add event statistics and metrics
- [ ] Create event rate monitoring

**Testing Phase 5:**
- [ ] Test event history with large datasets
- [ ] Verify search performance
- [ ] Test filtering and sorting functionality
- [ ] Validate live event monitoring
- [ ] Test event deletion and purging

---

## Phase 6: Chat & Web Output Integration
**Goal**: Provide customizable web output for streaming software

### 6.1 Chat Display System
- [ ] Create chat event bus/queue
- [ ] Implement chat message formatting
- [ ] Add customizable styling options:
  - [ ] Name colors
  - [ ] Text size and fonts
  - [ ] Background and transparency
  - [ ] Animation effects

### 6.2 Web Output Generation
- [ ] Create HTML template system
- [ ] Implement real-time chat display page
- [ ] Add CSS customization interface
- [ ] Create preview functionality
- [ ] Implement multiple output sources
- [ ] Add OBS integration documentation

### 6.3 Chat Output Configuration UI
- [ ] Create chat display configuration screen
- [ ] Add visual style editor
- [ ] Implement template selection
- [ ] Add preview functionality
- [ ] Create export/import for configurations

**Testing Phase 6:**
- [ ] Test chat display in various browsers
- [ ] Verify OBS integration
- [ ] Test style customization
- [ ] Validate real-time updates
- [ ] Test multiple concurrent displays

---

## Phase 7: Viewer Management System
**Goal**: Track and manage viewer interactions and permissions

### 7.1 Viewer Database
- [ ] Extend database schema for viewers
- [ ] Implement viewer tracking system
- [ ] Create unique viewer identification
- [ ] Add cross-platform viewer linking
- [ ] Implement viewer statistics

### 7.2 Viewer Management UI
- [ ] Create viewer management screen
- [ ] Add viewer search and filtering
- [ ] Implement permission management:
  - [ ] Moderator privileges
  - [ ] Super moderator privileges
  - [ ] TTS enable/disable per viewer
  - [ ] Chat display enable/disable per viewer
- [ ] Add viewer notes and tags
- [ ] Create viewer activity history

### 7.3 Viewer Permission System
- [ ] Implement role-based permissions
- [ ] Create permission inheritance system
- [ ] Add temporary permission assignments
- [ ] Implement permission audit logging

**Testing Phase 7:**
- [ ] Test viewer identification across platforms
- [ ] Verify permission system functionality
- [ ] Test viewer management UI
- [ ] Validate permission inheritance
- [ ] Test viewer statistics accuracy

---

## Phase 8: Platform Response System
**Goal**: Enable the application to respond to platform events

### 8.1 Response Framework
- [ ] Design response action system
- [ ] Implement command detection in events
- [ ] Create response queue management
- [ ] Add rate limiting for responses
- [ ] Implement response templates

### 8.2 Platform Response Implementation
- [ ] Implement Twitch chat response capability
- [ ] Add response authentication management
- [ ] Create response logging and audit
- [ ] Add response failure handling and retry

### 8.3 Response Configuration
- [ ] Create response management UI
- [ ] Add response template editor
- [ ] Implement response triggers and conditions
- [ ] Add response testing functionality

**Testing Phase 8:**
- [ ] Test response system with Twitch
- [ ] Verify rate limiting works correctly
- [ ] Test response template system
- [ ] Validate response logging
- [ ] Test error handling and retries

---

## Phase 9: System Commands & Automation
**Goal**: Implement command system for enhanced functionality

### 9.1 Command Framework
- [ ] Design command processing system
- [ ] Implement command parser
- [ ] Create command registry and routing
- [ ] Add command permissions and validation
- [ ] Implement command response formatting

### 9.2 Built-in Commands
- [ ] Implement `~voices` command (list available TTS voices)
- [ ] Implement `~setvoice` command (set personal TTS voice)
- [ ] Create command help system
- [ ] Add command usage statistics

### 9.3 System Commands UI
- [ ] Create command management screen
- [ ] Add command enable/disable toggles
- [ ] Implement command configuration
- [ ] Add custom command creation
- [ ] Create command testing interface

### 9.4 Extensible Command System
- [ ] Design plugin architecture for commands
- [ ] Create command development guide
- [ ] Implement command hot-reload capability

**Testing Phase 9:**
- [ ] Test all built-in commands
- [ ] Verify command permissions work correctly
- [ ] Test command configuration UI
- [ ] Validate command response system
- [ ] Test command extensibility

---

## Phase 10: Testing & Simulation Framework
**Goal**: Provide comprehensive testing capabilities

### 10.1 Event Simulation System
- [ ] Create mock streaming platform connectors
- [ ] Implement event generation tools
- [ ] Add realistic event timing simulation
- [ ] Create scenario-based testing
- [ ] Implement load testing capabilities

### 10.2 Testing UI
- [ ] Create testing/simulation screen
- [ ] Add event generation controls
- [ ] Implement scenario selection
- [ ] Add real-time testing metrics
- [ ] Create test result logging

### 10.3 Integration Testing Tools
- [ ] Create end-to-end testing scenarios
- [ ] Implement automated testing scripts
- [ ] Add performance benchmarking
- [ ] Create regression testing suite

**Testing Phase 10:**
- [ ] Test event simulation accuracy
- [ ] Verify all systems work with simulated events
- [ ] Test performance under load
- [ ] Validate testing UI functionality
- [ ] Test integration scenarios

---

## Phase 11: Polish & Production Readiness
**Goal**: Finalize the application for production use

### 11.1 User Experience Improvements
- [ ] Implement application themes
- [ ] Add keyboard shortcuts
- [ ] Create user onboarding flow
- [ ] Add contextual help system
- [ ] Implement application updates system

### 11.2 Performance Optimization
- [ ] Profile application performance
- [ ] Optimize memory usage
- [ ] Improve startup time
- [ ] Optimize database queries
- [ ] Add performance monitoring

### 11.3 Error Handling & Recovery
- [ ] Implement comprehensive error handling
- [ ] Add crash recovery system
- [ ] Create diagnostic reporting
- [ ] Implement graceful degradation
- [ ] Add error reporting to developers

### 11.4 Documentation & Support
- [ ] Create user manual
- [ ] Write installation guide
- [ ] Create troubleshooting guide
- [ ] Add FAQ section
- [ ] Create video tutorials

**Testing Phase 11:**
- [ ] Conduct full application testing
- [ ] Performance testing under various loads
- [ ] User acceptance testing
- [ ] Cross-platform compatibility testing
- [ ] Security and penetration testing

---

## Phase 12: Deployment & Distribution
**Goal**: Package and distribute the application

### 12.1 Application Packaging
- [ ] Finalize DMG creation for macOS
- [ ] **Complete Apple notarization for Catalina compatibility**
- [ ] **Test on multiple macOS versions (Catalina through latest)**
- [ ] Finalize EXE creation for Windows
- [ ] Create installation packages
- [ ] Add code signing certificates
- [ ] Implement update mechanism
- [ ] **Verify security permissions work correctly on Catalina**

### 12.2 Distribution Setup
- [ ] Setup GitHub Releases
- [ ] Create download landing page
- [ ] Implement usage analytics (privacy-compliant)
- [ ] Add crash reporting system
- [ ] Create support channels

### 12.3 Launch Preparation
- [ ] Beta testing program
- [ ] Final security review
- [ ] Legal compliance check
- [ ] Marketing materials preparation
- [ ] Support documentation finalization

**Final Testing:**
- [ ] Complete end-to-end testing
- [ ] **Cross-platform final validation (including macOS Catalina)**
- [ ] **Verify 64-bit compatibility across all components**
- [ ] Performance benchmarking
- [ ] Security audit completion
- [ ] User acceptance testing completion
- [ ] **Apple notarization verification**

---

## macOS Catalina Compatibility Requirements

### Critical Compatibility Notes
- **Release Date**: macOS Catalina was released October 7, 2019
- **64-bit Only**: Catalina dropped ALL 32-bit application support
- **Notarization Required**: Apps must be notarized by Apple to run without warnings
- **Enhanced Security**: Stricter Gatekeeper policies

### Java & Development Requirements
- **Minimum Java Version**: Java 17+ recommended for full Catalina compatibility
- **JavaFX**: Must use standalone JavaFX (not included in Java 11+)
- **All Dependencies**: Must be 64-bit compatible
- **Native Libraries**: Any JNI or native components must be 64-bit and signed

### Distribution Requirements for Catalina+
1. **Apple Developer Account**: Required for notarization
2. **Code Signing**: All executables and libraries must be signed
3. **Hardened Runtime**: Required for notarization
4. **Entitlements**: May need specific entitlements for certain APIs
5. **Notarization**: Submit to Apple for malware scanning

### Testing Strategy for Catalina
- **Test on actual Catalina systems** (not just newer versions)
- **Verify Gatekeeper behavior** with unsigned vs signed builds
- **Test security permissions** for file access, network, etc.
- **Validate installation process** doesn't trigger security warnings

### Development Environment Considerations
- **Building on Catalina**: Should work but newer Xcode versions may not be available
- **Building for Catalina**: Can build on newer macOS versions targeting Catalina
- **Docker/Dev Containers**: Ensure any containerized builds can target Catalina

---

## Technical Architecture Decisions

### Frontend Technology
- **JavaFX**: Modern Java UI framework with rich controls and styling
- **FXML**: Declarative UI design with CSS styling support
- **Scene Builder**: Visual UI design tool

### Backend Technology
- **Spring Framework**: Dependency injection and application framework
- **Event-driven Architecture**: Decoupled system with event bus
- **Embedded Database**: H2 or SQLite for local data storage
- **WebSocket**: Real-time communication for web outputs

### Build & Deployment
- **Maven**: Build automation and dependency management
- **jpackage**: Native application packaging (Java 14+ required for proper Catalina support)
- **GitHub Actions**: CI/CD pipeline
- **Apple Notarization**: Required for macOS Catalina+ distribution
- **Multi-stage builds**: Efficient Docker image creation

### Integration Technologies
- **WebSocket/EventSource**: Real-time web communication
- **REST APIs**: Platform integration
- **OAuth 2.0**: Secure platform authentication
- **AWS SDK**: Cloud services integration

---

## Success Metrics

### Functional Requirements
- [ ] Successfully connects to Twitch
- [ ] Processes all defined event types
- [ ] TTS functionality works with multiple voices
- [ ] Web output integrates with OBS
- [ ] Event history and search functionality
- [ ] Viewer management system functional
- [ ] System commands work correctly
- [ ] Platform response capability functional

### Performance Requirements
- [ ] Application starts in under 10 seconds
- [ ] Event processing latency under 100ms
- [ ] TTS generation under 5 seconds
- [ ] Supports 1000+ concurrent viewers
- [ ] Database queries under 100ms
- [ ] Memory usage under 1GB under normal load

### Quality Requirements
- [ ] Zero critical bugs in production
- [ ] 99%+ uptime during streaming sessions
- [ ] Cross-platform compatibility verified
- [ ] Security audit passed
- [ ] User acceptance criteria met

---

## Risk Mitigation

### Technical Risks
1. **Platform API Changes**: Implement abstraction layer and monitoring
2. **Performance Issues**: Regular profiling and load testing
3. **Cross-platform Compatibility**: Continuous testing on both platforms
4. **Third-party Dependencies**: Regular security updates and alternatives
5. **⚠️ macOS Catalina Specific Risks**:
   - **64-bit Only Requirement**: All components must be 64-bit
   - **Notarization Requirements**: Apple Developer account and proper signing
   - **Enhanced Security**: Gatekeeper restrictions may block unsigned apps
   - **Java Version Compatibility**: Must use Java 17+ for full Catalina support

### Project Risks
1. **Scope Creep**: Stick to defined phases and requirements
2. **Timeline Delays**: Regular milestone reviews and adjustments
3. **Resource Constraints**: Prioritize MVP features first
4. **User Adoption**: Early beta testing and feedback integration
5. **⚠️ Distribution Challenges**: Apple notarization process can be time-consuming

---

*This plan will be updated as development progresses and requirements evolve. Each completed checkbox represents a tested and validated feature.*
