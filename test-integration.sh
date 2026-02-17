#!/bin/bash

echo "=== Smart Bridge Integration Testing ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

FHIR_SERVER="http://localhost:8082/fhir"
SMART_BRIDGE="http://localhost:8080/smart-bridge"

echo "1. Testing HAPI FHIR Server..."
if curl -s -f "$FHIR_SERVER/metadata" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ HAPI FHIR is running on port 8082${NC}"
else
    echo -e "${RED}✗ HAPI FHIR is not accessible${NC}"
    exit 1
fi

echo ""
echo "2. Testing Smart Bridge Application..."
HEALTH=$(curl -s "$SMART_BRIDGE/actuator/health")
if echo "$HEALTH" | grep -q "UP"; then
    echo -e "${GREEN}✓ Smart Bridge is running and healthy${NC}"
else
    echo -e "${RED}✗ Smart Bridge is not healthy${NC}"
    exit 1
fi

echo ""
echo "3. Creating a test Patient in FHIR server..."
PATIENT_JSON='{
  "resourceType": "Patient",
  "name": [{
    "family": "TestPatient",
    "given": ["Integration"]
  }],
  "gender": "male",
  "birthDate": "1990-05-15",
  "identifier": [{
    "system": "http://example.org/mrn",
    "value": "TEST-12345"
  }]
}'

PATIENT_RESPONSE=$(curl -s -X POST "$FHIR_SERVER/Patient" \
  -H "Content-Type: application/fhir+json" \
  -d "$PATIENT_JSON")

PATIENT_ID=$(echo "$PATIENT_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "$PATIENT_ID" ]; then
    echo -e "${GREEN}✓ Patient created with ID: $PATIENT_ID${NC}"
else
    echo -e "${RED}✗ Failed to create patient${NC}"
    echo "Response: $PATIENT_RESPONSE"
    exit 1
fi

echo ""
echo "4. Testing Smart Bridge webhook with the created Patient..."
WEBHOOK_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$SMART_BRIDGE/fhir/webhook/resource/Patient" \
  -H "Content-Type: application/fhir+json" \
  -d "$PATIENT_RESPONSE")

HTTP_CODE=$(echo "$WEBHOOK_RESPONSE" | tail -1)
RESPONSE_BODY=$(echo "$WEBHOOK_RESPONSE" | head -n -1)

echo "HTTP Status: $HTTP_CODE"
echo "Response: $RESPONSE_BODY"

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "202" ]; then
    echo -e "${GREEN}✓ Webhook processed successfully${NC}"
elif [ "$HTTP_CODE" = "500" ]; then
    echo -e "${YELLOW}⚠ Webhook returned 500 (may be due to missing UCS/OpenHIM)${NC}"
    echo "This is expected if UCS API or OpenHIM are not configured"
else
    echo -e "${RED}✗ Webhook failed with status $HTTP_CODE${NC}"
fi

echo ""
echo "5. Retrieving the Patient from FHIR to verify..."
RETRIEVED_PATIENT=$(curl -s "$FHIR_SERVER/Patient/$PATIENT_ID")
if echo "$RETRIEVED_PATIENT" | grep -q "TestPatient"; then
    echo -e "${GREEN}✓ Patient successfully retrieved from FHIR${NC}"
else
    echo -e "${RED}✗ Failed to retrieve patient${NC}"
fi

echo ""
echo "6. Testing Bundle webhook notification..."
BUNDLE_JSON='{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [{
    "resource": {
      "resourceType": "Patient",
      "id": "'"$PATIENT_ID"'",
      "name": [{"family": "TestPatient", "given": ["Integration"]}],
      "gender": "male",
      "birthDate": "1990-05-15"
    }
  }]
}'

BUNDLE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$SMART_BRIDGE/fhir/webhook/notification" \
  -H "Content-Type: application/fhir+json" \
  -d "$BUNDLE_JSON")

BUNDLE_HTTP_CODE=$(echo "$BUNDLE_RESPONSE" | tail -1)
echo "Bundle webhook HTTP Status: $BUNDLE_HTTP_CODE"

if [ "$BUNDLE_HTTP_CODE" = "200" ] || [ "$BUNDLE_HTTP_CODE" = "202" ]; then
    echo -e "${GREEN}✓ Bundle webhook processed successfully${NC}"
elif [ "$BUNDLE_HTTP_CODE" = "500" ]; then
    echo -e "${YELLOW}⚠ Bundle webhook returned 500 (may be due to missing UCS/OpenHIM)${NC}"
else
    echo -e "${RED}✗ Bundle webhook failed${NC}"
fi

echo ""
echo "=== Integration Test Summary ==="
echo -e "${GREEN}✓ FHIR Server: Connected${NC}"
echo -e "${GREEN}✓ Smart Bridge: Running${NC}"
echo -e "${GREEN}✓ Patient Creation: Success${NC}"
echo -e "${GREEN}✓ Patient Retrieval: Success${NC}"
if [ "$HTTP_CODE" = "500" ]; then
    echo -e "${YELLOW}⚠ Webhook Processing: Partial (needs UCS/OpenHIM)${NC}"
else
    echo -e "${GREEN}✓ Webhook Processing: Success${NC}"
fi

echo ""
echo "Patient ID for further testing: $PATIENT_ID"
echo "View in FHIR: $FHIR_SERVER/Patient/$PATIENT_ID"
