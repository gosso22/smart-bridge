#!/bin/bash

# Quick test script for Smart Bridge local deployment

set -e

BASE_URL="http://localhost:8083/smart-bridge"
FHIR_URL="http://localhost:8082/fhir"

echo "🧪 Testing Smart Bridge Local Deployment"
echo "========================================"
echo ""

# Test 1: Health Check
echo "1️⃣  Testing Health Check..."
HEALTH=$(curl -s ${BASE_URL}/actuator/health)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo "✅ Health check passed"
else
    echo "❌ Health check failed"
    echo "$HEALTH"
    exit 1
fi
echo ""

# Test 2: HAPI FHIR Connection
echo "2️⃣  Testing HAPI FHIR Connection..."
FHIR_META=$(curl -s ${FHIR_URL}/metadata)
if echo "$FHIR_META" | grep -q "CapabilityStatement"; then
    echo "✅ HAPI FHIR is accessible"
else
    echo "❌ HAPI FHIR connection failed"
    exit 1
fi
echo ""

# Test 3: Ingest UCS Client
echo "3️⃣  Testing UCS to FHIR Ingestion..."
if [ -f test-data/test-ucs-client.json ]; then
    RESPONSE=$(curl -s -X POST ${BASE_URL}/api/sync/ingest \
        -H "Content-Type: application/json" \
        -d @test-data/test-ucs-client.json)
    
    if echo "$RESPONSE" | grep -q "success\|Patient"; then
        echo "✅ Ingestion successful"
        echo "Response: $RESPONSE"
    else
        echo "⚠️  Ingestion response: $RESPONSE"
    fi
else
    echo "⚠️  Test data file not found, skipping ingestion test"
fi
echo ""

# Test 4: Search for Patient in FHIR
echo "4️⃣  Testing Patient Search in FHIR..."
PATIENT_SEARCH=$(curl -s "${FHIR_URL}/Patient?identifier=12345-67890")
if echo "$PATIENT_SEARCH" | grep -q "Bundle"; then
    echo "✅ Patient search successful"
    PATIENT_COUNT=$(echo "$PATIENT_SEARCH" | grep -o '"total":[0-9]*' | grep -o '[0-9]*')
    echo "Found $PATIENT_COUNT patient(s)"
else
    echo "⚠️  Patient search returned unexpected result"
fi
echo ""

# Test 5: Check Metrics
echo "5️⃣  Testing Metrics Endpoint..."
METRICS=$(curl -s ${BASE_URL}/actuator/metrics)
if echo "$METRICS" | grep -q "names"; then
    echo "✅ Metrics endpoint accessible"
else
    echo "❌ Metrics endpoint failed"
fi
echo ""

echo "========================================"
echo "🎉 Testing complete!"
echo ""
echo "📊 View more details:"
echo "  - Health: ${BASE_URL}/actuator/health"
echo "  - Metrics: ${BASE_URL}/actuator/metrics"
echo "  - FHIR Patients: ${FHIR_URL}/Patient"
echo "  - RabbitMQ: http://localhost:15672"
echo "  - OpenHIM: http://localhost:9000"
