#!/bin/bash
# Smart Bridge E2E Testing Guide

BASE_URL="http://localhost:8080/smart-bridge"

echo "=== Smart Bridge E2E Testing ==="
echo ""
echo "Prerequisites:"
echo "  - UCS running on http://localhost:8081/ucs"
echo "  - FHIR server running on http://localhost:8082/fhir"
echo "  - Smart Bridge running on http://localhost:8080/smart-bridge"
echo ""

# Test 1: Trigger bulk sync
echo "Test 1: Trigger Bulk Sync (UCS -> FHIR)"
echo "========================================="
echo "This will pull ALL clients from UCS and push to FHIR"
echo ""
echo "Command:"
echo "  curl -X POST $BASE_URL/api/sync/bulk"
echo ""
read -p "Press Enter to execute..."
curl -X POST "$BASE_URL/api/sync/bulk"
echo ""
echo ""

# Test 2: Trigger incremental sync
echo "Test 2: Trigger Incremental Sync"
echo "================================="
echo "This will pull only NEW clients since last sync"
echo ""
echo "Command:"
echo "  curl -X POST $BASE_URL/api/sync/incremental"
echo ""
read -p "Press Enter to execute..."
curl -X POST "$BASE_URL/api/sync/incremental"
echo ""
echo ""

# Test 3: Check health
echo "Test 3: Check Application Health"
echo "================================="
echo "Command:"
echo "  curl $BASE_URL/actuator/health"
echo ""
read -p "Press Enter to execute..."
curl -s "$BASE_URL/actuator/health" | jq '.' 2>/dev/null || curl -s "$BASE_URL/actuator/health"
echo ""
echo ""

# Test 4: Check metrics
echo "Test 4: Check Sync Metrics"
echo "=========================="
echo "Command:"
echo "  curl $BASE_URL/actuator/metrics"
echo ""
read -p "Press Enter to execute..."
curl -s "$BASE_URL/actuator/metrics" | jq '.' 2>/dev/null || curl -s "$BASE_URL/actuator/metrics"
echo ""
echo ""

# Test 5: Check logs
echo "Test 5: Check Application Logs"
echo "==============================="
echo "Command:"
echo "  tail -f /tmp/smartbridge.log"
echo ""
echo "Press Ctrl+C to stop"
read -p "Press Enter to execute..."
tail -f /tmp/smartbridge.log

