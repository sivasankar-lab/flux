# Interaction Tracking & Personalization System

## How It Works

### 1. User Interaction Recording

Every time a user interacts with a post, the system records:
- **View Duration**: How long they viewed the card
- **Interaction Type**: Like, skip, or long read
- **Category**: Which category the post belongs to
- **Meta Information**: Intensity, pacing, and other narrative attributes

```
User views a "Tech & Innovation" post for 12 seconds
↓
System records: LONG_READ interaction
↓
Stores in interactions.json with metadata
```

### 2. Preference Analysis

The system analyzes all interactions to determine:
- **Preferred Categories**: Top 3 categories user engages with
- **Preferred Pacing**: Fast/Balanced/Slow based on dwell time
- **Preferred Depth**: Shallow/Moderate/Deep content
- **Engagement Level**: High/Medium based on likes and long reads

```
User has:
- 15 Tech posts with avg 10s dwell time
- 8 Business posts with avg 6s dwell time
- 5 Lifestyle posts with avg 3s dwell time

System determines:
- Preferred Categories: [Tech, Business, Lifestyle]
- Preferred Pacing: Slow (10s average)
- Preferred Depth: Deep
- Engagement Level: High (12 likes)
```

### 3. Personalized Seed Generation

Based on user preferences, the system:
1. Filters meta configs to match preferred categories
2. Adjusts narrative intensity based on engagement level
3. Sets pacing to match user preference
4. Generates 5 personalized seeds using Ollama
5. Stores them in next-seeds.json

```
For user preferring Tech + High Engagement:
↓
Generate seeds with:
- Category: Tech & Innovation
- Intensity: 8 (high end of range)
- Pacing: Slow
- Depth: Deep, thought-provoking content
```

## Usage Flow

### Initial Load
1. User visits the platform
2. System generates or retrieves user ID from localStorage
3. Loads initial seeds from `/v1/seeds/with-meta`
4. Displays cards with meta information

### During Browsing
1. User scrolls through feed
2. Frontend tracks dwell time for each card
3. Interactions recorded when:
   - Card leaves viewport (VIEW, SKIP, or LONG_READ)
   - User clicks like button (LIKE)
4. All interactions saved to `interactions.json`

### Loading More Content
1. User reaches end of initial seeds
2. System checks if personalized seeds exist
3. If yes: Loads from `next-seeds.json`
4. If no: Generates new seeds based on preferences
5. Continues infinite scroll with personalized content

### Category Filtering
1. User clicks category chip (e.g., "Tech")
2. Frontend calls `/v1/seeds/by-category/Tech & Innovation`
3. Only seeds from that category are displayed
4. Interactions continue to be recorded
5. Preferences updated in real-time

## Data Flow Diagram

```
┌─────────────┐
│   User      │
│   Browsing  │
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│  Card Display    │
│  (with meta)     │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  Dwell Tracking  │
│  Like Clicks     │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ POST /v1/        │
│ interactions/    │
│ record           │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ interactions.json│
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  Preference      │
│  Analysis        │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  Personalized    │
│  Seed Generation │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ next-seeds.json  │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  Load Next Seeds │
│  for User        │
└──────────────────┘
```

## Example Scenarios

### Scenario 1: New User
```
1. First visit → No interaction history
2. Loads generic seeds from all categories
3. User starts browsing (dwells on Tech & Finance posts)
4. System records interactions
5. On next visit → Sees more Tech & Finance content
```

### Scenario 2: Engaged User
```
1. User has 50+ interactions
2. Preferences show: Tech (high engagement), Fast pacing
3. System generates 5 personalized Tech seeds
4. User sees highly relevant content
5. Engagement increases further
```

### Scenario 3: Category Explorer
```
1. User typically likes Tech content
2. Clicks "Health" category chip
3. Browses Health content
4. Likes some posts (recorded)
5. Preferences now include Health
6. Future seeds include Health & Tech mix
```

## Testing the System

### 1. Record Some Interactions
```bash
curl -X POST http://localhost:8080/v1/interactions/record \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user",
    "seed_id": "seed_123",
    "interaction_type": "LIKE",
    "category": "Tech & Innovation",
    "dwell_time_ms": 8500
  }'
```

### 2. Check User Preferences
```bash
curl http://localhost:8080/v1/interactions/user/test_user/preferences
```

### 3. Generate Personalized Seeds
```bash
curl -X POST http://localhost:8080/v1/interactions/user/test_user/generate-next
```

### 4. Get Next Seeds
```bash
curl http://localhost:8080/v1/interactions/user/test_user/next-seeds
```

### 5. View Stats
```bash
curl http://localhost:8080/v1/interactions/stats
```

## File Structure

```
src/main/resources/
├── interactions.json          # All user interactions
├── next-seeds.json           # Personalized seeds by user
├── meta-configs.json         # Category configurations
└── seeds/                    # Generated seed files
    ├── Tech___Innovation-0.st
    ├── Tech___Innovation-1.st
    └── ...

src/main/java/com/example/socialmedia_poc/
├── model/
│   ├── Interaction.java      # Interaction model
│   └── SeedWithMeta.java     # Seed with metadata
├── service/
│   ├── InteractionService.java        # Interaction management
│   └── PersonalizedSeedService.java   # Personalization logic
└── controller/
    ├── InteractionController.java     # Interaction endpoints
    └── SeedController.java           # Enhanced with meta
```

## Benefits

1. **Personalization**: Each user gets unique content based on their behavior
2. **Engagement**: Higher engagement through relevant content
3. **Learning**: System continuously learns from interactions
4. **Diversity**: Balances preferences with content discovery
5. **Scalability**: JSON-based storage (can migrate to DB later)

## Future Enhancements

- [ ] Database integration (replace JSON files)
- [ ] Real-time seed generation during scroll
- [ ] A/B testing different meta configurations
- [ ] Collaborative filtering (similar users)
- [ ] Time-based preferences (morning vs evening)
- [ ] Content diversity enforcement
- [ ] Interaction decay (older interactions matter less)
