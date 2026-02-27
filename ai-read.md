# AI-READ: Flux — AI-Native Generative Social Media Platform

> **Purpose of this file**: Machine-readable project context for AI coding assistants, LLM agents, and automated tooling. Optimized for fast comprehension of architecture, data flow, APIs, and code structure.

---

## 1. PROJECT IDENTITY

| Field | Value |
|---|---|
| **Name** | Flux |
| **Type** | AI-native generative social media platform |
| **Concept** | Unlike traditional social media that serves existing human content, Flux generates content in real-time using LLMs, personalized per user via behavioral signals |
| **Language** | Java 11 |
| **Framework** | Spring Boot 2.7.18 |
| **Build** | Maven (mvnw wrapper included) |
| **Server Port** | 80 (requires sudo on macOS) |
| **Package** | `com.example.socialmedia_poc` |
| **Entry Point** | `SocialmediaPocApplication.java` — annotated with `@SpringBootApplication`, `@EnableScheduling` |

---

## 2. TECHNOLOGY STACK

### Backend
- **Spring Boot 2.7.18** — Web MVC + WebFlux (reactive HTTP client for LLM calls)
- **Java 11** — JDK 11.0.29 (Homebrew, `/opt/homebrew/opt/openjdk@11`)
- **Jackson + JSR310** — JSON serialization with `java.time.Instant` support
- **H2 Database** — In-memory (JPA starter present but unused; all data stored as JSON files)
- **WebClient** — Non-blocking HTTP client for all LLM API calls
- **@EnableScheduling** — Periodic pool health monitoring

### Frontend
- **Vanilla HTML/CSS/JS** — No framework
- **Fonts** — Inter, Space Grotesk (Google Fonts)
- **UI Style** — Dark glassmorphism, 3-column layout
- **Interaction Tracking** — IntersectionObserver for dwell-time measurement

### Dependencies (pom.xml)
```xml
spring-boot-starter-web          — REST controllers
spring-boot-starter-webflux      — Reactive WebClient for LLM APIs
spring-boot-starter-data-jpa     — JPA (present but unused)
h2                               — In-memory DB (runtime scope, unused)
jackson-datatype-jsr310          — Java 8+ time serialization
spring-boot-starter-test         — Test scope
```

---

## 3. LLM INTEGRATION ARCHITECTURE

### Provider Abstraction
```
LLMService (interface)
├── HuggingFaceService  — router.huggingface.co (default)
├── GrokCloudService    — api.x.ai
└── OllamaService       — localhost:11434
```

### Interface: `LLMService.java`
```java
String generateContent(String prompt) throws Exception;
String generateContent(String systemMessage, String prompt) throws Exception;
String getProviderName();
```

### Provider Selection: `LLMConfig.java`
- Reads `llm.provider` from `application.properties`
- Creates `@Bean @Primary activeLLMService` that routes to the selected implementation
- Valid values: `huggingface` (default) | `grok` | `ollama`
- All services injected via `@Qualifier("activeLLMService")`

### Provider Details

| Provider | Model | Base URL | API Style | Timeout | Auth Header |
|---|---|---|---|---|---|
| **HuggingFace** | `zai-org/GLM-5:novita` | `https://router.huggingface.co` | OpenAI-compatible `/v1/chat/completions` | 90s | `Bearer {hf_token}` |
| **Grok (xAI)** | `grok-3-mini-fast` | `https://api.x.ai` | OpenAI-compatible `/v1/chat/completions` | 60s | `Bearer {xai_key}` |
| **Ollama** | `deepseek-r1:7b` | `http://localhost:11434` | Ollama native `/api/generate` | default | None |

### LLM Request Shape (HuggingFace/Grok)
```json
{
  "model": "<model_name>",
  "messages": [
    {"role": "system", "content": "<system_message>"},
    {"role": "user", "content": "<prompt>"}
  ],
  "max_tokens": 300,
  "temperature": 0.8,
  "stream": false
}
```

### System Message Construction
Dynamically built from `MetaConfig.language` field:
```
Base: "You are Flux, a generative social media platform..."
+ Language instruction: "Write this post in: Tamil or English or Tanglish."
+ Tamil script hint: "Use Tamil script (தமிழ்) naturally."
+ Tanglish hint: "Tanglish = Tamil words in English letters"
```

### Output Sanitization: `cleanContent()`
Applied to all LLM outputs:
1. Strip `<think>...</think>` tags (common in reasoning models like DeepSeek)
2. Strip all remaining HTML tags
3. Trim whitespace
4. Truncate to 50 words max (with "..." suffix)

---

## 4. CONTENT CATEGORIES

6 knowledge-focused categories, all trilingual (Tamil, English, Tanglish):

| Category | Intensity | Pacing | Triggers | Key Vocabulary |
|---|---|---|---|---|
| **History & Society** | 6-9 | Balanced | Curiosity, Awe, Pride, Debate | ancient, empire, revolution, civilization |
| **Science & How Things Work** | 5-8 | Balanced | Aha-Moment, Wonder, Problem-Solve | quantum, experiment, discovery, breakthrough |
| **Psychology & Human Behavior** | 6-9 | Slow | Self-Reflection, Relatability, Vulnerability | subconscious, bias, pattern, mindset |
| **Technology & Future** | 7-9 | Fast | Awe, FOMO, Obsolescence, Ambition | AI, disrupt, autonomous, singularity |
| **Philosophy & Life Questions** | 5-8 | Slow | Existential, Wonder, Exploration | meaning, existence, purpose, paradox |
| **Health & Lifestyle Tips** | 3-6 | Balanced | Optimization, Longevity, Relatability | routine, optimal, habit, balance |

### MetaConfig Schema (per category in `meta-configs.json`)
```json
{
  "category": "string",
  "meta_config": {
    "intensity_range": [int, int],
    "pacing": "Fast|Balanced|Slow",
    "language": ["Tamil", "English", "Tanglish"],
    "triggers": ["string"],
    "dwell_logic": {
      "on_slow_read": "string",
      "on_fast_swipe": "string"
    },
    "vocabulary_weight": { "word": double }
  }
}
```

---

## 5. DATA ARCHITECTURE

### Storage Pattern
**All data is stored as JSON files on the filesystem** — no SQL/database is used for application data.

```
src/main/resources/
├── meta-configs.json              ← 6 category definitions
├── meta-template.json             ← Template for meta configs
├── users.json                     ← All registered users array
├── seeds/                         ← Pre-generated seed content (.st files)
│   ├── History___Society-0.st
│   ├── Science___How_Things_Work-3.st
│   └── ...
├── user-data/
│   ├── pool.json                  ← Shared post pool (cross-user)
│   ├── {userId}/                  ← Per-user directory
│   │   ├── interactions.json      ← User's interaction history
│   │   ├── wall.json              ← User's personalized wall
│   │   ├── next-seeds.json        ← Pre-generated seeds queue
│   │   └── interest-profile.json  ← Computed interest profile
│   └── system/                    ← System-generated content
```

### Seed File Naming Convention
`{Category}-{index}.st` where category uses `___` for ` & `, `__` for ` (`, `_-` for `)`, `_` for spaces.
Example: `Psychology___Human_Behavior-5.st` → category "Psychology & Human Behavior"

---

## 6. DATA MODELS (Java)

### `User.java`
```
userId: String (UUID)
username: String
email: String
displayName: String
sessionToken: String (nullable, format: "session_{timestamp}_{uuid}")
createdAt: Instant
lastLogin: Instant
```

### `Interaction.java`
```
userId: String
seedId: String
interactionType: InteractionType (enum)
dwellTimeMs: Long (nullable)
category: String
timestamp: Instant
metaData: InteractionMetaData (nested)

InteractionType: VIEW | LIKE | SKIP | BOOKMARK | LONG_READ
InteractionMetaData: { intensity: Integer, pacing: String, scrollDepth: Double }
```

### `InterestProfile.java`
```
userId: String
categoryScores: Map<String, Double>      ← Normalized 0.0-1.0
categoryLikes: Map<String, Integer>
categorySkips: Map<String, Integer>
categoryDwellMs: Map<String, Long>
categoryInteractionCount: Map<String, Integer>
preferredPacing: String                   ← "fast" | "moderate" | "slow"
contentLengthPref: String                 ← "short" | "medium" | "long"
consecutiveSkips: int
totalInteractions: int
totalLikes: int
totalSkips: int
avgSessionDepth: int
lastUpdated: Instant
interactionCountAtLastUpdate: int
```

### `PoolPost.java`
```
postId: String (UUID)
content: String
category: String
source: PostSource (enum: SEED | GENERATED)
generatedForInterest: String (nullable)
engagementScore: double                    ← Computed: likes×3 + longReads×2 + views - skips×2
viewCount: int
likeCount: int
longReadCount: int
skipCount: int
totalDwellMs: long
avgDwellMs: long
createdAt: Instant

Engagement Formula:
  score = (likeCount × 3.0) + (longReadCount × 2.0) + (viewCount × 1.0) - (skipCount × 2.0)
  + (avgDwellMs > 5000 ? 1.0 : 0.0)
```

### `WallPost.java`
```
postId: String
content: String
category: String
source: PostSource (SEED | GENERATED)
batch: int
addedAt: Instant
engagementScore: double
```
Constructed from `PoolPost.toWallPost(batchNumber)`.

### `SeedWithMeta.java`
```
seedId: String
content: String
category: String
metaConfig: MetaConfig
generationContext: GenerationContext
  ├── basedOnInteraction: String
  ├── userPreferenceSignal: String
  └── narrativeDepth: String
```

### `Meta.java`
```
category: String
metaConfig: MetaConfig       ← JSON field "meta_config"
```

### `MetaConfig.java`
```
intensityRange: List<Integer>             ← JSON "intensity_range"
pacing: String
triggers: List<String>
language: List<String>                    ← e.g. ["Tamil", "English", "Tanglish"]
dwellLogic: Map<String, String>           ← JSON "dwell_logic"
vocabularyWeight: Map<String, Double>     ← JSON "vocabulary_weight"
```

---

## 7. SERVICE LAYER

### Core Generation Pipeline

```
SeedGenerationService
  ├── Reads meta-configs.json
  ├── For each category × 10 iterations:
  │     ├── buildSystemMessage(MetaConfig) → dynamic language-aware system prompt
  │     ├── constructPrompt(Meta) → prompt with intensity, pacing, triggers, vocabulary
  │     ├── llmService.generateContent(systemMsg, prompt)
  │     ├── cleanContent(response) → sanitize LLM output
  │     └── Write to seeds/{category}-{i}.st
  └── Triggered via POST /v1/seeds/generate

PersonalizedSeedService
  ├── analyzeUserPreference(userId) → UserPreference
  ├── Filter categories by user preferences
  ├── constructPersonalizedPrompt(Meta, UserPreference)
  │     ├── Adjusts intensity based on engagement level
  │     ├── Sets pacing from user preference
  │     └── Includes depth preference (deep/shallow)
  ├── llmService.generateContent(systemMsg, prompt)
  └── Returns List<SeedWithMeta>
  
  Also: generateForCategory(userId, category, count) — used by trigger system
```

### Async Content Generation

```
AsyncContentGeneratorService
  ├── Single daemon thread (ExecutorService.newSingleThreadExecutor)
  ├── ConcurrentLinkedQueue<GenerationTask> — thread-safe task queue
  ├── enqueue(userId, category, count, reason) → instant return
  ├── enqueueBulk(userId, Map<category,count>, reason)
  ├── drainQueue() → processes tasks sequentially (one LLM call at a time)
  └── @Scheduled monitorPoolHealth() — every 120s, initial delay 60s
        ├── Checks each category count in pool
        ├── MIN_POSTS_PER_CATEGORY = 12
        └── Enqueues deficit tasks capped at 3 per category per check

GenerationTask: { userId, category, count, reason }
Reasons: "DEEP_INTEREST", "ENGAGEMENT_DROP", "POOL_EXHAUSTION", "POOL_HEALTH", "WALL_DEFICIT", "USER_REQUEST"
```

### Recommendation Engine (PostPoolService)

```
Pool Initialization (@PostConstruct):
  ├── Load pool.json
  ├── If empty, load all .st seed files → PoolPost.fromSeedFile()
  └── Save to pool.json

Recommendation: recommend(InterestProfile, seenPostIds, count)
  ├── Cold Start (0 interactions): round-robin across categories
  └── Scoring:
        score = (categoryMatch × 0.4) + (engagement × 0.3) + (freshness × 0.2) + (novelty × 0.1)
        
        categoryMatch: profile.categoryScores[post.category]
        engagement: post.engagementScore / 3.0 (normalized)
        freshness: 1.0 - (ageHours / 168) (7-day decay)
        novelty: 0.5 + (interestScore × 0.5) for GENERATED posts
        
  Diversity: max 40% from any single category in results
```

### Interest Profile Building (InterestProfileService)

```
Recalculation trigger: every 5 new interactions
Recency weighting: interactions within 24h count 2×

Type weights:
  LIKE      → +3.0
  BOOKMARK  → +2.5
  LONG_READ → +2.0
  VIEW      → +1.0
  SKIP      → -1.0

Category scores normalized to 0.0 – 1.0 range
Pacing derived from avg dwell time:
  >8000ms → slow, >4000ms → moderate, else → fast

Similar users: cosine similarity on category score vectors (findSimilarUsers)
```

### Smart Triggers (GenerationTriggerService)

```
evaluate(userId, interaction, profile, seenPostIds) → TriggerResult

DEEP_INTEREST:
  Condition: 3+ LIKEs in same category in last 20 interactions
  Action: enqueue 3 posts for that category

ENGAGEMENT_DROP:
  Condition: 3+ consecutive SKIPs
  Action: enqueue 3 posts for highest-scoring non-skipped category

POOL_EXHAUSTION:
  Condition: <5 unseen pool posts matching top 3 interest categories
  Action: enqueue 3 posts each for top 2 categories

All triggers enqueue to AsyncContentGeneratorService (no blocking LLM calls).
TriggerResult carries type + message only — no inline posts.
```

### User Service (UserService)

```
Storage: src/main/resources/users.json (JSON array of User objects)
Per-user directory: src/main/resources/user-data/{userId}/
  Created on registration with empty: interactions.json, next-seeds.json, wall.json

Session tokens: "session_{timestamp}_{uuid}" stored in User.sessionToken
Auth model: Username-only login (no password) — POC simplification
```

### Wall Service (WallService)

```
getWall(userId):
  ├── Load wall.json
  ├── If empty, initializeWall() → recommend 10 from pool
  └── Return wall posts

generateNextBatch(userId):
  ├── Get InterestProfile
  ├── Collect seen post IDs
  ├── poolService.recommend(profile, seenIds, 10) — NO sync LLM calls
  ├── If <10 results, enqueue background generation for deficit
  └── Save and return new batch

Never makes synchronous LLM calls — all wall operations are instant.
```

---

## 8. REST API ENDPOINTS

### Wall Controller (`/v1/wall`)

| Method | Path | Description | Response |
|---|---|---|---|
| GET | `/{userId}` | Get user's wall (auto-init if empty) | `{status, total, posts[]}` |
| POST | `/{userId}/next` | Generate next batch from pool | `{status, batch, posts[]}` |
| GET | `/{userId}/batch/{batch}` | Get specific batch | `{status, batch, count, posts[]}` |
| GET | `/{userId}/stats` | Wall statistics | `{total_posts, total_batches, seed_posts, generated_posts, categories{}}` |
| POST | `/{userId}/reset` | Reset wall (re-init) | `{status, posts[]}` |
| GET | `/pool/stats` | Shared pool stats | `{total_posts, seed_posts, generated_posts, categories{}}` |

### Interaction Controller (`/v1/interactions`)

| Method | Path | Description | Response |
|---|---|---|---|
| POST | `/record` | Record interaction + evaluate triggers | `{status, trigger, trigger_message, queue_depth}` |
| GET | `/user/{userId}` | Get user's interactions | `Interaction[]` |
| GET | `/user/{userId}/preferences` | Analyzed preferences | `UserPreference` |
| GET | `/user/{userId}/profile` | Interest profile | `InterestProfile` |
| POST | `/user/{userId}/generate-next` | Enqueue personalized generation | `{status: "queued", queue_depth}` |
| GET | `/pool/stats` | Pool stats + queue depth | `{..., generation_queue_depth}` |
| GET | `/stats` | Global interaction stats | `{total_interactions, unique_users, interaction_types{}}` |

### Seed Controller (`/v1/seeds`)

| Method | Path | Description | Response |
|---|---|---|---|
| POST | `/generate` | Bulk generate seeds (10 per category, synchronous) | `"Seed generation completed."` |
| GET | `/random` | 10 random seed texts | `String[]` |
| GET | `/with-meta?limit=N` | Seeds with metadata | `SeedWithMeta[]` |
| GET | `/by-category/{category}` | Seeds for specific category | `SeedWithMeta[]` |

### User Controller (`/v1/users`)

| Method | Path | Description | Response |
|---|---|---|---|
| POST | `/register` | Create account | `{status, user}` |
| POST | `/login` | Login by username | `{status, user}` |
| GET | `/session/{token}` | Validate session | `{status, user}` |
| POST | `/logout` | Invalidate session | `{status}` |
| GET | `/{userId}` | Get user profile | `{status, user}` |
| GET | `/all` | List all users (admin) | `{status, count, users[]}` |

### Legacy/Utility

| Method | Path | Description |
|---|---|---|
| GET | `/v1/flux/feed` | Legacy Ollama direct generation |
| GET | `/hello` | Health check → `"Hello, World!"` |

---

## 9. FRONTEND ARCHITECTURE

### Pages
- **`/login.html`** — Login/Register with username-only auth
- **`/index.html`** — Main feed (redirects to login if no session)

### Auth Flow (auth.js)
```
1. Check localStorage for flux_session_token
2. Validate via GET /v1/users/session/{token}
3. If valid → redirect to /index.html
4. Login: POST /v1/users/login {username}
5. Register: POST /v1/users/register {username, email, display_name}
6. Store: flux_session_token, flux_user_id, flux_user in localStorage
```

### Feed Flow (script.js)
```
1. On DOMContentLoaded: validate session → load wall
2. fetchAllSeeds() → GET /v1/wall/{userId}
3. Render cards with IntersectionObserver (animation + dwell tracking)
4. Dwell time tracking:
   - >8s → LONG_READ
   - <2s → SKIP
   - else → VIEW
5. Like button click → LIKE interaction
6. POST /v1/interactions/record → trigger evaluation
7. On scroll bottom → POST /v1/wall/{userId}/next (load next batch)
8. Category chip click → GET /v1/seeds/by-category/{category}
```

### Category Chip Mapping (script.js)
```javascript
categoryMap = {
  'History':    'History & Society',
  'Science':    'Science & How Things Work',
  'Psychology': 'Psychology & Human Behavior',
  'Technology': 'Technology & Future',
  'Philosophy': 'Philosophy & Life Questions',
  'Health':     'Health & Lifestyle Tips'
}
```

### UI Components
- **Nav bar**: Stream/Explore/Saved tabs, notification bell, profile avatar
- **Left sidebar**: Stats (cards viewed, avg dwell), category chips, about text
- **Main feed**: Card grid with skeleton loading, infinite scroll
- **Right panel**: Trending topics (hardcoded), generate seeds button, how-it-works info
- **Cards**: Category badge, source badge (AI Generated), like button, title, body
- **Toast notifications**: Trigger messages (DEEP_INTEREST, ENGAGEMENT_DROP, POOL_EXHAUSTION)
- **FAB**: Scroll-to-top button

---

## 10. KEY DATA FLOWS

### Flow A: New User First Visit
```
login.html → POST /v1/users/login
           → Store session in localStorage
           → Redirect to index.html
           → GET /v1/wall/{userId}
           → Wall empty → initializeWall()
           → poolService.recommend(emptyProfile, {}, 10)
           → Cold-start: round-robin across categories
           → Return 10 diverse posts
           → Render card grid
```

### Flow B: User Reads Content (Dwell Tracking)
```
Card enters viewport → IntersectionObserver starts timer
Card leaves viewport → Calculate dwellTimeMs
                     → Classify: VIEW(<8s), LONG_READ(>8s), SKIP(<2s)
                     → POST /v1/interactions/record
                     → interactionService.recordInteraction(interaction)
                     → poolService.recordEngagement(postId, type, dwellMs)
                     → profileService.onInteraction(userId, interaction)
                     │   └── If totalInteractions % 5 == 0: full profile rebuild
                     → triggerService.evaluate(userId, interaction, profile, seenIds)
                     │   └── If trigger fires: asyncContentGenerator.enqueue(...)
                     → Return {status, trigger, trigger_message, queue_depth}
                     → Frontend shows toast if trigger != NONE
```

### Flow C: Async Content Generation
```
Trigger or health monitor enqueues task
→ AsyncContentGeneratorService.enqueue(userId, category, count, reason)
→ Task added to ConcurrentLinkedQueue
→ kickOffProcessing() → if not already processing, submit to executor
→ Background thread: drainQueue()
   → personalizedSeedService.generateForCategory(userId, category, count)
      → analyzeUserPreference(userId)
      → Construct personalized prompt with MetaConfig
      → llmService.generateContent(systemMsg, prompt) — blocks for LLM response
      → cleanContent(response)
      → Return List<SeedWithMeta>
   → PoolPost.fromGenerated(seed, category) for each result
   → poolService.addToPool(poolPosts) — write to pool.json
→ Next user wall request picks up new posts from pool
```

### Flow D: Infinite Scroll (Next Batch)
```
User scrolls to bottom → All rendered cards exhausted
→ POST /v1/wall/{userId}/next
→ wallService.generateNextBatch(userId)
   → Get InterestProfile
   → poolService.recommend(profile, seenIds, 10)
   → If <10 results: enqueue background generation for deficit
   → Append to wall.json
   → Return new batch instantly (no LLM wait)
→ Frontend renders new cards with batch separator
```

### Flow E: Pool Health Monitor
```
@Scheduled every 120s (initial delay 60s)
→ poolService.getPoolStats() → count posts per category
→ For each category: if count < 12 (MIN_POSTS_PER_CATEGORY)
   → deficit = min(needed, 3)
→ If any deficits: enqueueBulk("system", deficits, "POOL_HEALTH")
→ Background thread generates and adds to pool
```

---

## 11. FILE MAP

### Java Source Files (29 total)

```
src/main/java/com/example/socialmedia_poc/
├── SocialmediaPocApplication.java         ← @SpringBootApplication @EnableScheduling
├── config/
│   └── LLMConfig.java                     ← @Configuration: LLM provider router bean
├── controller/
│   ├── FluxController.java                ← /v1/flux/feed (legacy Ollama endpoint)
│   ├── HelloController.java               ← /hello (health check)
│   ├── InteractionController.java         ← /v1/interactions/** (record, stats, triggers)
│   ├── SeedController.java                ← /v1/seeds/** (generate, query seeds)
│   ├── UserController.java                ← /v1/users/** (auth, profile)
│   └── WallController.java                ← /v1/wall/** (personalized wall, pool stats)
├── model/
│   ├── Interaction.java                   ← Interaction + InteractionType enum + MetaData
│   ├── InterestProfile.java               ← Per-user interest scores and preferences
│   ├── Meta.java                          ← Category + MetaConfig wrapper
│   ├── MetaConfig.java                    ← Category config (intensity, pacing, language, triggers)
│   ├── PoolPost.java                      ← Shared pool post + engagement metrics + scoring
│   ├── SeedWithMeta.java                  ← Generated content + generation context
│   ├── User.java                          ← User account model
│   └── WallPost.java                      ← Per-user wall post (from PoolPost)
└── service/
    ├── LLMService.java                    ← Interface: generateContent(), getProviderName()
    ├── HuggingFaceService.java            ← HuggingFace Inference API implementation
    ├── GrokCloudService.java              ← xAI Grok API implementation
    ├── OllamaService.java                 ← Local Ollama implementation
    ├── SeedGenerationService.java         ← Bulk seed generation (10 per category)
    ├── PersonalizedSeedService.java       ← User-preference-aware content generation
    ├── AsyncContentGeneratorService.java  ← Background thread + queue + pool health
    ├── PostPoolService.java               ← Shared pool CRUD + recommendation scoring
    ├── WallService.java                   ← Per-user wall management
    ├── InteractionService.java            ← Record interactions + UserPreference analysis
    ├── InterestProfileService.java        ← Build/update interest profiles + similarity
    ├── GenerationTriggerService.java      ← Smart trigger evaluation (DEEP_INTEREST, etc.)
    └── UserService.java                   ← User CRUD + session management + file paths
```

### Frontend Files

```
src/main/resources/static/
├── index.html        ← Main feed page (3-column glassmorphism layout)
├── login.html        ← Login/register page
├── js/
│   ├── script.js     ← Feed rendering, interaction tracking, infinite scroll (~605 lines)
│   └── auth.js       ← Auth flow, session management (~170 lines)
└── css/
    └── style.css     ← Dark theme glassmorphism styles
```

### Resource Files

```
src/main/resources/
├── application.properties    ← Server + LLM provider configuration
├── meta-configs.json         ← 6 category definitions with MetaConfig
├── meta-template.json        ← Template for meta configs
├── users.json                ← Registered users (JSON array)
├── seeds/                    ← Generated .st seed files (60 files: 6 categories × 10)
└── user-data/
    ├── pool.json             ← Shared post pool
    └── {userId}/             ← Per-user data directories
```

---

## 12. CONFIGURATION REFERENCE

### application.properties
```properties
# LLM provider switch
llm.provider=huggingface              # huggingface | grok | ollama

# HuggingFace (default)
huggingface.api-key=hf_*****
huggingface.model=zai-org/GLM-5:novita
huggingface.baseurl=https://router.huggingface.co

# Grok (xAI)
grok.api-key=xai-*****
grok.model=grok-3-mini-fast
grok.baseurl=https://api.x.ai

# Ollama (local)
ollama.baseurl=http://localhost:11434
ollama.model=deepseek-r1:7b

# Server
server.port=80
```

---

## 13. BUILD AND RUN

```bash
# Build
./mvnw clean package -DskipTests

# Run (port 80 requires sudo on macOS)
sudo java -jar target/socialmedia-poc-0.0.1-SNAPSHOT.jar

# Or via Maven
sudo ./mvnw spring-boot:run

# Access
http://localhost/login.html    ← Login/Register
http://localhost/index.html    ← Main feed (requires auth)
http://localhost/hello          ← Health check
```

---

## 14. DESIGN PATTERNS AND CONSTRAINTS

### Patterns Used
- **Strategy Pattern**: `LLMService` interface with 3 implementations, selected at startup
- **Producer-Consumer**: `AsyncContentGeneratorService` with `ConcurrentLinkedQueue`
- **Observer Pattern**: `IntersectionObserver` for dwell-time tracking on frontend
- **Event-Driven Triggers**: `GenerationTriggerService` evaluates behavioral signals
- **Cold Start Strategy**: Round-robin category sampling for new users
- **Diversity Enforcement**: Max 40% of recommendations from any single category

### Constraints / Known Limitations
- **No database**: All state is JSON files — not suitable for concurrent writes at scale
- **No password auth**: Username-only login (POC simplification)
- **Single-threaded generation**: One LLM call at a time in background
- **Port 80**: Requires `sudo` on macOS
- **File paths hardcoded**: `src/main/resources/` paths assume project root as working directory
- **No WebSocket**: Frontend polls via infinite scroll, no real-time push
- **H2 & JPA**: Dependencies present but completely unused
- **Session tokens**: Stored in `users.json`, no expiry mechanism

### Thread Safety
- `AsyncContentGeneratorService`: Thread-safe queue (`ConcurrentLinkedQueue`) + `AtomicBoolean` for processing flag
- `PostPoolService`: File I/O not synchronized — potential race condition under concurrent writes
- All other services: Request-scoped (thread-safe via Spring's default singleton + stateless design)

---

## 15. INTERACTION TYPE CLASSIFICATION

Frontend automatically classifies interactions based on dwell time:
```
dwellTimeMs < 2000   → SKIP
dwellTimeMs >= 8000  → LONG_READ  
2000 ≤ dwellTimeMs < 8000 → VIEW
```
Plus explicit:
- Like button click → `LIKE`
- Bookmark action → `BOOKMARK` (not yet implemented in UI)

---

## 16. JACKSON SERIALIZATION NOTES

### JSON Field Name Mapping
Java models use `@JsonProperty` for snake_case JSON ↔ camelCase Java mapping:
- `Interaction`: `user_id`, `seed_id`, `interaction_type`, `dwell_time_ms`, `meta_data`
- `User`: `user_id`, `display_name`, `session_token`, `created_at`, `last_login`
- `PoolPost`: `post_id`, `engagement_score`, `view_count`, `like_count`, etc.
- `MetaConfig`: `intensity_range`, `vocabulary_weight`, `dwell_logic`

### Time Serialization
All `Instant` fields serialized as ISO-8601 strings via `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS = false`.

---

## 17. DEPENDENCY INJECTION GRAPH

```
LLMConfig
  └── @Bean activeLLMService → selects HuggingFaceService|GrokCloudService|OllamaService

SeedGenerationService ← @Qualifier("activeLLMService") LLMService
PersonalizedSeedService ← InteractionService, @Qualifier("activeLLMService") LLMService
AsyncContentGeneratorService ← PersonalizedSeedService, PostPoolService
InteractionService ← UserService
InterestProfileService ← UserService, InteractionService
GenerationTriggerService ← PostPoolService, AsyncContentGeneratorService, InteractionService
WallService ← UserService, PostPoolService, InterestProfileService, AsyncContentGeneratorService
PostPoolService ← (standalone, no injections)
UserService ← (standalone, no injections)

WallController ← WallService, PostPoolService
InteractionController ← InteractionService, InterestProfileService, GenerationTriggerService,
                         PostPoolService, WallService, AsyncContentGeneratorService
SeedController ← SeedGenerationService
UserController ← UserService
FluxController ← OllamaService
```

---

## 18. PROMPT ENGINEERING

### Seed Generation Prompt Template
```
Generate a seed post for the category '{category}'.
Respond with EXACTLY 50 words or less. Only provide the final post content.
The post should have an intensity between {min} and {max},
a {pacing} pacing,
and trigger feelings of {trigger1}, {trigger2}, ...
Use words like {vocab1}, {vocab2}, ...
```

### Personalized Prompt Additions
```
+ "Make the content more detailed and thought-provoking." (if depth == "deep")
+ "Keep the content lightweight and easy to digest." (if depth == "shallow")
+ "an intensity around {maxIntensity}" (if engagement == "high")
+ "a {preferredPacing} pacing"
```

### System Message Base
```
You are Flux, a generative social media platform that creates highly engaging,
culturally relevant short-form content.
NEVER include <think> tags, reasoning, or explanations. Output ONLY the post content.
Keep posts under 50 words.
Write this post in: {language1} or {language2} or {language3}.
Use Tamil script (தமிழ்) naturally. Tanglish (Tamil written in English letters) is also acceptable.
```

---

## 19. ENGAGEMENT SCORING FORMULA

### Post Engagement (PoolPost.recalculateEngagement)
```
engagementScore = (likeCount × 3.0)
                + (longReadCount × 2.0)
                + (viewCount × 1.0)
                - (skipCount × 2.0)
                + (avgDwellMs > 5000 ? 1.0 : 0.0)
```

### Recommendation Score (PostPoolService.scorePost)
```
score = (categoryMatch × 0.4)     ← profile.categoryScores[category], 0.0-1.0
      + (engagement × 0.3)         ← engagementScore/3.0, capped at 1.0
      + (freshness × 0.2)          ← 1.0 - (ageHours/168), 7-day decay
      + (novelty × 0.1)            ← 0.5 for SEED, 0.5-1.0 for GENERATED
```

### Interest Profile Score Weights
```
LIKE:      +3.0 × recencyWeight
BOOKMARK:  +2.5 × recencyWeight
LONG_READ: +2.0 × recencyWeight
VIEW:      +1.0 × recencyWeight
SKIP:      -1.0 × recencyWeight

recencyWeight = 2.0 if within last 24h, else 1.0
```

---

## 20. GLOSSARY

| Term | Meaning |
|---|---|
| **Seed** | Pre-generated content stored as `.st` files, created via `SeedGenerationService` |
| **Pool** | Shared post collection (`pool.json`) used for cross-user recommendations |
| **Wall** | Per-user personalized feed (`wall.json`), populated from pool |
| **Batch** | Numbered group of wall posts loaded together (batch 1, 2, 3...) |
| **Trigger** | Behavioral signal that enqueues async content generation |
| **Dwell Time** | Duration (ms) a card is visible in the user's viewport |
| **Cold Start** | First-visit user with no interactions; uses diverse round-robin sampling |
| **Tanglish** | Tamil written in English letters (e.g., "Vera level mass da!") |
| **MetaConfig** | Per-category generation parameters (intensity, pacing, triggers, language) |
| **Interest Profile** | Computed per-user model of category preferences, rebuilt every 5 interactions |
