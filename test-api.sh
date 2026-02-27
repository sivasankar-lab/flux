#!/bin/bash

# Flux Platform Test Script
# This script demonstrates the interaction tracking and personalization features

echo "======================================"
echo "Flux Platform - API Testing"
echo "======================================"
echo ""

BASE_URL="http://localhost:8080"
USER_ID="test_user_$(date +%s)"

echo "Using User ID: $USER_ID"
echo ""

# Test 1: Check if server is running
echo "1. Testing server connection..."
curl -s $BASE_URL/hello > /dev/null
if [ $? -eq 0 ]; then
    echo "✓ Server is running"
else
    echo "✗ Server is not running. Please start the application first."
    exit 1
fi
echo ""

# Test 2: Generate seeds (if needed)
echo "2. Checking available seeds..."
SEEDS_COUNT=$(curl -s "$BASE_URL/v1/seeds/random" | jq '. | length')
if [ "$SEEDS_COUNT" -gt 0 ]; then
    echo "✓ Found $SEEDS_COUNT seeds"
else
    echo "⚠ No seeds found. Generating..."
    curl -X POST -s "$BASE_URL/v1/seeds/generate"
    echo "✓ Seed generation started (this may take a few minutes)"
fi
echo ""

# Test 3: Get seeds with metadata
echo "3. Fetching seeds with metadata..."
curl -s "$BASE_URL/v1/seeds/with-meta?limit=3" | jq '.[0] | {seed_id, category, content: (.content | .[0:50] + "...")}'
echo "✓ Seeds retrieved with metadata"
echo ""

# Test 4: Record interactions
echo "4. Recording sample interactions..."

# Interaction 1: Long read on Tech
curl -X POST -s "$BASE_URL/v1/interactions/record" \
  -H "Content-Type: application/json" \
  -d "{
    \"user_id\": \"$USER_ID\",
    \"seed_id\": \"seed_tech_1\",
    \"interaction_type\": \"LONG_READ\",
    \"category\": \"Tech & Innovation\",
    \"dwell_time_ms\": 12000,
    \"meta_data\": {
      \"intensity\": 7,
      \"pacing\": \"Balanced\"
    }
  }" | jq '.message'

# Interaction 2: Like on Business
curl -X POST -s "$BASE_URL/v1/interactions/record" \
  -H "Content-Type: application/json" \
  -d "{
    \"user_id\": \"$USER_ID\",
    \"seed_id\": \"seed_business_1\",
    \"interaction_type\": \"LIKE\",
    \"category\": \"Business & Startups\",
    \"dwell_time_ms\": 8500,
    \"meta_data\": {
      \"intensity\": 8,
      \"pacing\": \"Fast\"
    }
  }" | jq '.message'

# Interaction 3: Skip on lifestyle
curl -X POST -s "$BASE_URL/v1/interactions/record" \
  -H "Content-Type: application/json" \
  -d "{
    \"user_id\": \"$USER_ID\",
    \"seed_id\": \"seed_lifestyle_1\",
    \"interaction_type\": \"SKIP\",
    \"category\": \"Lifestyle / Daily\",
    \"dwell_time_ms\": 1200
  }" | jq '.message'

# Interaction 4: Long read on Tech (again)
curl -X POST -s "$BASE_URL/v1/interactions/record" \
  -H "Content-Type: application/json" \
  -d "{
    \"user_id\": \"$USER_ID\",
    \"seed_id\": \"seed_tech_2\",
    \"interaction_type\": \"LONG_READ\",
    \"category\": \"Tech & Innovation\",
    \"dwell_time_ms\": 15000,
    \"meta_data\": {
      \"intensity\": 8,
      \"pacing\": \"Slow\"
    }
  }" | jq '.message'

echo "✓ Recorded 4 sample interactions"
echo ""

# Test 5: Get user interactions
echo "5. Retrieving user interactions..."
curl -s "$BASE_URL/v1/interactions/user/$USER_ID" | jq 'length as $count | "Total interactions: \($count)"'
echo ""

# Test 6: Analyze user preferences
echo "6. Analyzing user preferences..."
curl -s "$BASE_URL/v1/interactions/user/$USER_ID/preferences" | jq '.'
echo ""

# Test 7: Generate personalized seeds
echo "7. Generating personalized seeds..."
GENERATE_RESULT=$(curl -X POST -s "$BASE_URL/v1/interactions/user/$USER_ID/generate-next")
echo "$GENERATE_RESULT" | jq '.message'
echo ""

# Test 8: Get next seeds
echo "8. Retrieving personalized seeds..."
curl -s "$BASE_URL/v1/interactions/user/$USER_ID/next-seeds" | jq '.[0] | {seed_id, category, generation_context}'
echo ""

# Test 9: Get by category
echo "9. Testing category filtering..."
curl -s "$BASE_URL/v1/seeds/by-category/Tech%20%26%20Innovation" | jq 'length as $count | "Tech & Innovation seeds: \($count)"'
echo ""

# Test 10: Platform statistics
echo "10. Platform statistics..."
curl -s "$BASE_URL/v1/interactions/stats" | jq '.'
echo ""

echo "======================================"
echo "All tests completed successfully! ✓"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Open http://localhost:8080/index.html in your browser"
echo "2. Browse the feed and interact with cards"
echo "3. Check the sidebar for your stats"
echo "4. Try filtering by category"
echo ""
echo "Your test user ID: $USER_ID"
echo "View your data:"
echo "  Interactions: curl $BASE_URL/v1/interactions/user/$USER_ID"
echo "  Preferences:  curl $BASE_URL/v1/interactions/user/$USER_ID/preferences"
echo "  Next Seeds:   curl $BASE_URL/v1/interactions/user/$USER_ID/next-seeds"
echo ""
