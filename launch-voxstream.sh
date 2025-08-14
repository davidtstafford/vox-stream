#!/bin/bash

# VoxStream Application Launcher Script
# macOS Catalina+ and Windows 10+ compatible startup script
# Performs system validation and launches the application with proper error handling

set -e

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="VoxStream"
APP_VERSION="1.0.0-SNAPSHOT"
MIN_JAVA_VERSION=17
LOG_FILE="${SCRIPT_DIR}/logs/launcher.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Create logs directory if it doesn't exist
mkdir -p "$(dirname "$LOG_FILE")"

# Logging function
log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

# Print colored output
print_color() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Print banner
print_banner() {
    echo ""
    print_color $BLUE "=================================="
    print_color $BLUE "  VoxStream Application Launcher  "
    print_color $BLUE "  Version: $APP_VERSION"
    print_color $BLUE "=================================="
    echo ""
}

# Check if running on macOS
is_macos() {
    [[ "$OSTYPE" == "darwin"* ]]
}

# Check if running on Windows (Git Bash/WSL)
is_windows() {
    [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || -n "$WSL_DISTRO_NAME" ]]
}

# Get macOS version
get_macos_version() {
    if is_macos; then
        sw_vers -productVersion
    else
        echo "N/A"
    fi
}

# Check if macOS version is supported (10.15+)
check_macos_version() {
    if ! is_macos; then
        return 0  # Not macOS, skip check
    fi
    
    local version=$(get_macos_version)
    local major_version=$(echo "$version" | cut -d. -f1)
    local minor_version=$(echo "$version" | cut -d. -f2)
    
    if [[ $major_version -gt 10 ]] || [[ $major_version -eq 10 && $minor_version -ge 15 ]]; then
        return 0  # Supported
    else
        return 1  # Not supported
    fi
}

# Get Java version
get_java_version() {
    if command -v java >/dev/null 2>&1; then
        java -version 2>&1 | head -n 1 | cut -d'"' -f2
    else
        echo "N/A"
    fi
}

# Check if Java version is supported
check_java_version() {
    if ! command -v java >/dev/null 2>&1; then
        return 1  # Java not found
    fi
    
    local version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    local major_version
    
    if [[ $version == 1.* ]]; then
        # Old format (1.8.0_xxx)
        major_version=$(echo "$version" | cut -d. -f2)
    else
        # New format (17.0.1)
        major_version=$(echo "$version" | cut -d. -f1)
    fi
    
    if [[ $major_version -ge $MIN_JAVA_VERSION ]]; then
        return 0  # Supported
    else
        return 1  # Not supported
    fi
}

# Check system architecture
check_architecture() {
    local arch=$(uname -m)
    if is_macos && [[ "$arch" != "x86_64" && "$arch" != "arm64" ]]; then
        return 1  # macOS requires 64-bit
    fi
    return 0  # Supported
}

# Validate system requirements
validate_system() {
    log "INFO" "Validating system requirements..."
    
    local os_name=$(uname -s)
    local os_version="N/A"
    local arch=$(uname -m)
    local java_version=$(get_java_version)
    
    if is_macos; then
        os_version=$(get_macos_version)
    elif is_windows; then
        os_version="Windows (detected)"
    fi
    
    print_color $BLUE "System Information:"
    echo "  OS: $os_name $os_version"
    echo "  Architecture: $arch"
    echo "  Java: $java_version"
    echo ""
    
    log "INFO" "System: $os_name $os_version, Arch: $arch, Java: $java_version"
    
    # Check macOS version
    if is_macos && ! check_macos_version; then
        print_color $RED "❌ Error: macOS Catalina (10.15) or newer is required"
        print_color $YELLOW "   Current version: $(get_macos_version)"
        log "ERROR" "macOS version not supported: $(get_macos_version)"
        return 1
    fi
    
    # Check architecture on macOS
    if is_macos && ! check_architecture; then
        print_color $RED "❌ Error: 64-bit architecture required on macOS"
        print_color $YELLOW "   Current architecture: $arch"
        log "ERROR" "Architecture not supported on macOS: $arch"
        return 1
    fi
    
    # Check Java version
    if ! check_java_version; then
        print_color $RED "❌ Error: Java $MIN_JAVA_VERSION or newer is required"
        print_color $YELLOW "   Current Java: $java_version"
        print_color $YELLOW "   Please install Java $MIN_JAVA_VERSION+ from: https://adoptium.net/"
        log "ERROR" "Java version not supported: $java_version"
        return 1
    fi
    
    print_color $GREEN "✅ System requirements validated successfully"
    log "INFO" "System requirements validation passed"
    return 0
}

# Find Maven executable
find_maven() {
    if [[ -x "$SCRIPT_DIR/mvnw" ]]; then
        echo "$SCRIPT_DIR/mvnw"
    elif command -v mvn >/dev/null 2>&1; then
        echo "mvn"
    else
        return 1
    fi
}

# Build the application
build_application() {
    log "INFO" "Building application..."
    print_color $BLUE "Building VoxStream..."
    
    local maven_cmd=$(find_maven)
    if [[ $? -ne 0 ]]; then
        print_color $RED "❌ Error: Maven not found"
        log "ERROR" "Maven executable not found"
        return 1
    fi
    
    # Build all modules with Maven (install to local repo)
    if "$maven_cmd" clean install -q > "$LOG_FILE.build" 2>&1; then
        print_color $GREEN "✅ Build completed successfully"
        log "INFO" "Application build completed"
        return 0
    else
        print_color $RED "❌ Error: Build failed"
        print_color $YELLOW "   Check build log: $LOG_FILE.build"
        log "ERROR" "Application build failed"
        return 1
    fi
}

# Launch the application
launch_application() {
    log "INFO" "Launching VoxStream application..."
    print_color $BLUE "Starting VoxStream..."
    
    local maven_cmd=$(find_maven)
    if [[ $? -ne 0 ]]; then
        print_color $RED "❌ Error: Maven not found"
        log "ERROR" "Maven executable not found"
        return 1
    fi
    
    # Launch JavaFX application
    log "INFO" "Executing: $maven_cmd javafx:run -f voxstream-frontend/pom.xml"
    print_color $GREEN "🚀 Launching VoxStream..."
    
    cd "$SCRIPT_DIR"
    "$maven_cmd" javafx:run -f voxstream-frontend/pom.xml
    
    local exit_code=$?
    if [[ $exit_code -eq 0 ]]; then
        log "INFO" "Application exited normally"
    else
        log "ERROR" "Application exited with code: $exit_code"
        print_color $RED "❌ Application exited with error code: $exit_code"
    fi
    
    return $exit_code
}

# Main function
main() {
    print_banner
    log "INFO" "VoxStream launcher started"
    
    # Validate system
    if ! validate_system; then
        print_color $RED "System validation failed. Cannot start VoxStream."
        log "ERROR" "System validation failed"
        exit 1
    fi
    
    # Build application
    if ! build_application; then
        print_color $RED "Build failed. Cannot start VoxStream."
        log "ERROR" "Build failed"
        exit 2
    fi
    
    # Launch application
    launch_application
    local exit_code=$?
    
    log "INFO" "VoxStream launcher finished with exit code: $exit_code"
    exit $exit_code
}

# Handle Ctrl+C
trap 'echo ""; print_color $YELLOW "Launcher interrupted"; log "INFO" "Launcher interrupted by user"; exit 130' INT

# Run main function
main "$@"
