#!/bin/bash

# Smart Bridge Application Startup Script
# This script helps start the application with proper environment configuration

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Smart Bridge Application Startup${NC}"
echo "=================================="

# Check Java version
echo -n "Checking Java version... "
if ! command -v java &> /dev/null; then
    echo -e "${RED}FAILED${NC}"
    echo "Java is not installed. Please install Java 17 or higher."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}FAILED${NC}"
    echo "Java 17 or higher is required. Found version: $JAVA_VERSION"
    exit 1
fi
echo -e "${GREEN}OK${NC} (Java $JAVA_VERSION)"

# Check Maven
echo -n "Checking Maven... "
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}FAILED${NC}"
    echo "Maven is not installed. Please install Maven 3.8 or higher."
    exit 1
fi
echo -e "${GREEN}OK${NC}"

# Load environment variables
ENV_FILE="$PROJECT_ROOT/smart-bridge-application/src/main/resources/.env"
if [ -f "$ENV_FILE" ]; then
    echo -e "${GREEN}Loading environment from .env${NC}"
    export $(cat "$ENV_FILE" | grep -v '^#' | xargs)
else
    echo -e "${YELLOW}Warning: .env file not found${NC}"
    echo "Using default configuration values"
fi

# Set profile
PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
echo "Profile: $PROFILE"

# Check RabbitMQ (optional)
RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
echo -n "Checking RabbitMQ connection... "
if nc -z "$RABBITMQ_HOST" 5672 2>/dev/null; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${YELLOW}WARNING${NC}"
    echo "Cannot connect to RabbitMQ at $RABBITMQ_HOST:5672"
    echo "Message queue features will not work until RabbitMQ is available"
fi

# Build if requested
if [ "$1" == "--build" ] || [ "$1" == "-b" ]; then
    echo ""
    echo "Building application..."
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests
    echo -e "${GREEN}Build complete${NC}"
fi

# Start application
echo ""
echo "Starting Smart Bridge application..."
echo "Press Ctrl+C to stop"
echo ""

cd "$PROJECT_ROOT"
mvn spring-boot:run \
    -pl smart-bridge-application \
    -Dspring-boot.run.profiles="$PROFILE" \
    -Dspring-boot.run.jvmArguments="-Xmx512m -Xms256m"
