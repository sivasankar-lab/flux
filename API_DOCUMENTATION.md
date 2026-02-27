# Flux API Documentation

## Overview
This document describes the API endpoints for the Flux social media platform, including interaction tracking and personalized seed generation.

## Interaction API

### Record User Interaction
**Endpoint:** `POST /v1/interactions/record`

Records user interactions with posts (views, likes, reading time, etc.)

**Request Body:**
```json
{
  "user_id": "user_12345",
  "seed_id": "seed_abc123",
  "interaction_type": "LIKE",
  "dwell_time_ms": 5000,
  "category": "Tech & Innovation",
  "meta_data": {
    "intensity": 7,
    "pacing": "Fast",
    "scroll_depth": 0.85
  }
}
```

**Interaction Types:**
- `VIEW` - User viewed the post
- `LIKE` - User liked the post
- `SKIP` - User skipped quickly (< 2 seconds)
- `BOOKMARK` - User bookmarked the post
- `LONG_READ` - User spent significant time reading (> 8 seconds)

**Response:**
```json
{
  "status": "success",
  "message": "Interaction recorded successfully"
}
```

**Storage:** Interactions are stored in `src/main/resources/interactions.json`

---

### Get User Interactions
**Endpoint:** `GET /v1/interactions/user/{userId}`

Retrieves all interactions for a specific user.

**Response:**
```json
[
  {
    "user_id": "user_12345",
    "seed_id": "seed_abc123",
    "interaction_type": "LIKE",
    "dwell_time_ms": 5000,
    "category": "Tech & Innovation",
    "timestamp": "2026-02-16T10:30:00Z",
    "meta_data": {
      "intensity": 7,
      "pacing": "Fast"
    }
  }
]
```

---

### Get User Preferences
**Endpoint:** `GET /v1/interactions/user/{userId}/preferences`

Analyzes user interactions and returns preference profile.

**Response:**
```json
{
  "preferred_categories": ["Tech & Innovation", "Business & Startups", "Educational / How-To"],
  "preferred_pacing": "Fast",
  "preferred_depth": "moderate",
  "engagement_level": "high"
}
```

**Preference Logic:**
- **Pacing:** Based on average dwell time
  - Fast: < 4 seconds
  - Balanced: 4-8 seconds
  - Slow: > 8 seconds
- **Depth:** Correlates with pacing
  - shallow, moderate, deep
- **Engagement:** Based on likes and long reads
  - high: > 5 likes or > 3 long reads
  - medium: otherwise

---

### Get Next Seeds
**Endpoint:** `GET /v1/interactions/user/{userId}/next-seeds`

Retrieves personalized seeds for the user based on their interaction history.

**Response:**
```json
[
  {
    "seed_id": "550e8400-e29b-41d4-a716-446655440000",
    "content": "Breaking: AI systems now autonomously...",
    "category": "Tech & Innovation",
    "meta_config": {
      "intensity_range": [5, 8],
      "pacing": "Balanced",
      "triggers": ["Awe", "Novelty", "Obsolescence"]
    },
    "generation_context": {
      "based_on_interaction": "user_preference_analysis",
      "user_preference_signal": "high",
      "narrative_depth": "moderate"
    }
  }
]
```

**Storage:** Next seeds are stored in `src/main/resources/next-seeds.json` organized by user ID.

---

### Generate Next Seeds
**Endpoint:** `POST /v1/interactions/user/{userId}/generate-next`

Generates new personalized seeds based on user preferences and saves them.

**Response:**
```json
{
  "status": "success",
  "message": "Generated 5 personalized seeds",
  "seeds": [...]
}
```

---

### Get Interaction Statistics
**Endpoint:** `GET /v1/interactions/stats`

Returns overall platform interaction statistics.

**Response:**
```json
{
  "total_interactions": 1523,
  "unique_users": 42,
  "interaction_types": {
    "VIEW": 800,
    "LIKE": 350,
    "LONG_READ": 250,
    "SKIP": 100,
    "BOOKMARK": 23
  }
}
```

---

## Seed API

### Get Random Seeds (Legacy)
**Endpoint:** `GET /v1/seeds/random`

Returns 10 random seeds as plain text (backward compatible).

**Response:**
```json
[
  "Breaking: The future of AI...",
  "Discover the secret to...",
  ...
]
```

---

### Get Seeds with Metadata
**Endpoint:** `GET /v1/seeds/with-meta?limit=10`

Returns seeds with full metadata including category and meta configuration.

**Query Parameters:**
- `limit` (default: 10) - Number of seeds to return

**Response:**
```json
[
  {
    "seed_id": "550e8400-e29b-41d4-a716-446655440000",
    "content": "Breaking: The future of AI...",
    "category": "Tech & Innovation",
    "meta_config": {
      "intensity_range": [5, 8],
      "pacing": "Balanced",
      "triggers": ["Awe", "Novelty", "Obsolescence"],
      "dwell_logic": {
        "on_slow_read": "simulate_a_specific_2030_timeline_prediction",
        "on_fast_swipe": "introduce_a_shocking_automation_stat"
      },
      "vocabulary_weight": {
        "neural": 0.8,
        "obsolete": 0.9,
        "autonomous": 0.8
      }
    }
  }
]
```

---

### Get Seeds by Category
**Endpoint:** `GET /v1/seeds/by-category/{category}`

Returns up to 5 seeds from a specific category.

**Path Parameters:**
- `category` - Category name (e.g., "Tech & Innovation", "Health & Wellness")

**Response:**
```json
[
  {
    "seed_id": "550e8400-e29b-41d4-a716-446655440000",
    "content": "...",
    "category": "Tech & Innovation",
    "meta_config": {...}
  }
]
```

**Available Categories:**
- Educational / How-To
- Behind-the-Scenes (BTS)
- Personal Finance
- Health & Wellness
- Tech & Innovation
- Travel & Adventure
- Business & Startups
- Entertainment / Pop
- Lifestyle / Daily
- Parenting / Family

---

### Generate Seeds
**Endpoint:** `POST /v1/seeds/generate`

Generates new seed posts for all categories.

**Response:**
```
Seed generation completed.
```

---

## Data Storage

### interactions.json
Stores all user interactions with the following structure:
```json
[
  {
    "user_id": "user_12345",
    "seed_id": "seed_abc123",
    "interaction_type": "LIKE",
    "dwell_time_ms": 5000,
    "category": "Tech & Innovation",
    "timestamp": "2026-02-16T10:30:00Z",
    "meta_data": {...}
  }
]
```

### next-seeds.json
Stores personalized seeds per user:
```json
{
  "user_12345": [
    {
      "seed_id": "...",
      "content": "...",
      "category": "...",
      "meta_config": {...},
      "generation_context": {...}
    }
  ],
  "user_67890": [...]
}
```

---

## Frontend Integration

The frontend automatically:
1. Generates or retrieves a user ID stored in localStorage
2. Tracks dwell time for each card
3. Records interactions (VIEW, LIKE, SKIP, LONG_READ)
4. Fetches seeds with metadata
5. Displays category badges and like buttons

**Key Features:**
- Automatic interaction recording based on scroll behavior
- Like button on each card
- Category filtering
- Personalized seed loading based on interaction history

---

## Usage Example

```javascript
// Record a like
await fetch('/v1/interactions/record', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    user_id: 'user_12345',
    seed_id: 'seed_abc123',
    interaction_type: 'LIKE',
    category: 'Tech & Innovation'
  })
});

// Get user preferences
const prefs = await fetch('/v1/interactions/user/user_12345/preferences')
  .then(r => r.json());

// Get personalized seeds
const nextSeeds = await fetch('/v1/interactions/user/user_12345/next-seeds')
  .then(r => r.json());
```

---

## Configuration

**application.properties**
```properties
ollama.baseurl=http://localhost:11434
ollama.model=llama3.2
```

**Meta Configurations**
Located in `src/main/resources/meta-configs.json` - defines narrative parameters for each category including intensity ranges, pacing, triggers, and vocabulary weights.
