#!/bin/bash

# Smart Bridge Local Deployment Script
# This script automates the local deployment process

set -e

echo "🚀 Smart Bridge Local Deployment"
echo "================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed${NC}"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java is not installed${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven is not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ All prerequisites met${NC}"
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠️  .env file not found${NC}"
    echo "Creating .env from template..."
    cp smart-bridge-application/src/main/resources/.env.template .env
    echo -e "${YELLOW}⚠️  Please edit .env file with your UCS system details${NC}"
    echo "Press Enter to continue after editing .env..."
    read
fi

# Start Docker services
echo "🐳 Starting Docker services..."
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be healthy (this may take 2-3 minutes)..."
sleep 10

# Wait for HAPI FHIR
echo -n "Waiting for HAPI FHIR..."
until curl -sf http://localhost:8082/fhir/metadata > /dev/null 2>&1; do
    echo -n "."
    sleep 5
done
echo -e " ${GREEN}✅${NC}"

# Wait for OpenHIM
echo -n "Waiting for OpenHIM Core..."
until curl -sf http://localhost:8080/heartbeat > /dev/null 2>&1; do
    echo -n "."
    sleep 5
done
echo -e " ${GREEN}✅${NC}"

# Wait for RabbitMQ
echo -n "Waiting for RabbitMQ..."
until curl -sf -u smartbridge:smartbridge123 http://localhost:15672/api/overview > /dev/null 2>&1; do
    echo -n "."
    sleep 5
done
echo -e " ${GREEN}✅${NC}"

echo ""
echo "✅ All Docker services are running!"
echo ""

# Build Smart Bridge
echo "🔨 Building Smart Bridge application..."
mvn clean package -DskipTests

echo ""
echo "✅ Build complete!"
echo ""

# Display service URLs
echo "📍 Service URLs:"
echo "================================"
echo "Smart Bridge:      http://localhost:8083/smart-bridge"
echo "HAPI FHIR:         http://localhost:8082/fhir"
echo "OpenHIM Console:   http://localhost:9000"
echo "OpenHIM Core:      http://localhost:8080"
echo "RabbitMQ Mgmt:     http://localhost:15672"
echo "Health Check:      http://localhost:8083/smart-bridge/actuator/health"
echo ""

echo "🔐 Default Credentials:"
echo "================================"
echo "OpenHIM Console:   root@openhim.org / openhim-password"
echo "RabbitMQ:          smartbridge / smartbridge123"
echo ""

echo "📝 Next Steps:"
echo "================================"
echo "1. Configure OpenHIM (see LOCAL_DEPLOYMENT.md Step 4)"
echo "2. Start Smart Bridge:"
echo "   mvn spring-boot:run -pl smart-bridge-application -Dspring-boot.run.profiles=dev"
echo ""
echo "3. Test the integration:"
echo "   curl -X POST http://localhost:8083/smart-bridge/api/sync/ingest \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d @test-data/test-ucs-client.json"
echo ""
echo "For detailed instructions, see LOCAL_DEPLOYMENT.md"
echo ""

echo -e "${GREEN}🎉 Infrastructure setup complete!${NC}"
