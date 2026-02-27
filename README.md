# Flux - AI-Native Social Media Platform

Flux is the world's first AI-native, generative social media platform that generates content in real-time specifically for each viewer. Unlike traditional platforms that serve a library of existing content, Flux creates an Infinite Narrative Stream that evolves based on user behavior.

## Features

### User Session Management (NEW!)
- **User Registration**: Create accounts with username, email, and display name
- **Session-Based Authentication**: Secure session tokens for persistent login
- **Per-User Data Storage**: Each user has dedicated JSON files for interactions and feeds
- **User Profiles**: Display name shown in navigation bar
- **Logout Support**: Clean session termination

### Core Platform
- **Infinite Narrative Stream**: AI-generated content that evolves with user interaction
- **Dwell-Time Signals**: Longer viewing leads to deeper stories; quick skips pivot topics
- **Personalized Content**: Every feed is unique, tailored to individual users
- **World-Class UI**: Modern, responsive design with glassmorphism effects
- **Automatic Personalized Loading**: Fetch personalized seeds when scrolling past initial content

### Interaction Tracking System
- **Automatic Recording**: Tracks views, likes, skip behavior, and reading time
- **User Preferences**: Analyzes interactions to build preference profiles
- **Smart Categorization**: 10 distinct content categories with meta configurations
- **Engagement Analytics**: Real-time stats on user behavior
- **Per-User Storage**: Interactions stored in user-specific JSON files

### Personalization Engine
- **Preference Analysis**: Determines preferred categories, pacing, and depth
- **Dynamic Content**: Generates personalized seeds based on interaction history
- **Meta-Driven Generation**: Uses category-specific configurations for narrative control
- **Adaptive Learning**: Continuously improves recommendations
- **On-Demand Generation**: Creates personalized content when user scrolls to end of feed

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (React-like)                 │
│  • World-class UI with gradient effects                 │
│  • Automatic interaction tracking                       │
│  • Like buttons, category filters, infinite scroll      │
└───────────────────┬─────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────┐
│              Spring Boot Backend                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │  InteractionController                          │   │
│  │  • Record interactions                          │   │
│  │  • Analyze preferences                          │   │
│  │  • Generate personalized seeds                  │   │
│  └─────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────┐   │
│  │  SeedController                                 │   │
│  │  • Serve seeds with metadata                    │   │
│  │  • Category filtering                           │   │
│  │  • Random seed selection                        │   │
│  └─────────────────────────────────────────────────┘   │
└───────────────────┬─────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────┐
│              Ollama LLM Service                          │
│  • Local LLM (llama3.2)                                 │
│  • Generates narrative content                          │
│  • Uses meta configurations for control                 │
└─────────────────────────────────────────────────────────┘
```

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.6+
- [Ollama](https://ollama.ai/) installed and running locally
- Ollama model downloaded: `ollama pull llama3.2`

### Installation

1. **Clone the repository**
   ```bash
   cd socialmedia-poc
   ```

2. **Start Ollama service**
   ```bash
   ollama serve
   ```

3. **Build the project**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

5. **Open in browser**
   ```
   http://localhost:8080/index.html
   ```

### Generate Initial Seeds

Before using the platform, generate seed content:

**Option 1: Via UI**
- Click "Generate New Seeds" button in the right panel

**Option 2: Via API**
```bash
curl -X POST http://localhost:8080/v1/seeds/generate
```

This will create 100 seed posts (10 per category) in `src/main/resources/seeds/`

## API Endpoints

### Seed Management
- `GET /v1/seeds/random` - Get 10 random seeds (legacy)
- `GET /v1/seeds/with-meta?limit=10` - Get seeds with metadata
- `GET /v1/seeds/by-category/{category}` - Filter by category
- `POST /v1/seeds/generate` - Generate new seeds

### Interaction Tracking
- `POST /v1/interactions/record` - Record user interaction
- `GET /v1/interactions/user/{userId}` - Get user's interactions
- `GET /v1/interactions/user/{userId}/preferences` - Get user preferences
- `GET /v1/interactions/user/{userId}/next-seeds` - Get personalized seeds
- `POST /v1/interactions/user/{userId}/generate-next` - Generate personalized seeds
- `GET /v1/interactions/stats` - Platform statistics

### Flux Feed
- `GET /v1/flux/feed` - Generate real-time narrative card

See [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for detailed API documentation.

## Content Categories

1. **Educational / How-To** - Learning and skill development
2. **Behind-the-Scenes (BTS)** - Authentic, unfiltered content
3. **Personal Finance** - Money management and investment
4. **Health & Wellness** - Physical and mental well-being
5. **Tech & Innovation** - Technology and future trends
6. **Travel & Adventure** - Exploration and discovery
7. **Business & Startups** - Entrepreneurship and business
8. **Entertainment / Pop** - Pop culture and entertainment
9. **Lifestyle / Daily** - Everyday life and routines
10. **Parenting / Family** - Family life and parenting

Each category has unique meta configurations controlling:
- Intensity range (0-10)
- Pacing (Fast/Balanced/Slow)
- Psychological triggers
- Dwell-time logic
- Vocabulary weights

## How It Works

### User Journey

1. **First Visit**
   - System generates unique user ID
   - Loads initial seeds from all categories
   - UI displays cards with metadata

2. **Browsing**
   - User scrolls through feed
   - System tracks dwell time per card
   - Interactions recorded automatically:
     - SKIP: < 2 seconds
     - VIEW: 2-8 seconds
     - LONG_READ: > 8 seconds
   - Like button for explicit feedback

3. **Personalization**
   - System analyzes interaction patterns
   - Determines preferred categories
   - Identifies pacing preference (Fast/Balanced/Slow)
   - Calculates engagement level

4. **Adaptive Content**
   - Generates personalized seeds
   - Adjusts intensity based on engagement
   - Matches pacing to user preference
   - Prioritizes preferred categories

5. **Continuous Learning**
   - Every interaction refines the model
   - Content becomes more relevant over time
   - Balances preferences with discovery

### Interaction Recording

```javascript
// Automatic tracking
{
  "user_id": "user_abc123",
  "seed_id": "seed_xyz789",
  "interaction_type": "LONG_READ",
  "dwell_time_ms": 12500,
  "category": "Tech & Innovation",
  "meta_data": {
    "intensity": 7,
    "pacing": "Balanced",
    "scroll_depth": 0.85
  }
}
```

### Preference Analysis

```javascript
// Computed from interactions
{
  "preferred_categories": ["Tech & Innovation", "Business & Startups"],
  "preferred_pacing": "Fast",
  "preferred_depth": "moderate",
  "engagement_level": "high"
}
```

## Configuration

### application.properties
```properties
ollama.baseurl=http://localhost:11434
ollama.model=llama3.2
```

### Meta Configurations
Located in `src/main/resources/meta-configs.json`. Define narrative parameters:
```json
{
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
```

## Data Storage

- `interactions.json` - All user interactions
- `next-seeds.json` - Personalized seeds per user
- `seeds/` - Generated seed files organized by category

## UI Features

### Navigation
- Logo and branding
- Feed tabs (Stream/Explore/Saved)
- Notification center
- User profile

### Main Feed
- Infinite scrolling
- Skeleton loaders
- Staggered animations
- Category badges
- Like buttons

### Sidebar
- User statistics (cards viewed, avg dwell time)
- Category filters
- About section

### Right Panel
- Trending topics
- Quick actions
- How it works guide

### Responsive Design
- Mobile-first approach
- Tablet and desktop layouts
- Glassmorphism effects
- Smooth transitions

## Testing

### 1. Generate Seeds
```bash
curl -X POST http://localhost:8080/v1/seeds/generate
```

### 2. Browse Feed
Open http://localhost:8080/index.html and scroll through content

### 3. Record Interaction
```bash
curl -X POST http://localhost:8080/v1/interactions/record \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user",
    "seed_id": "seed_123",
    "interaction_type": "LIKE",
    "category": "Tech & Innovation"
  }'
```

### 4. View Preferences
```bash
curl http://localhost:8080/v1/interactions/user/test_user/preferences
```

### 5. Generate Personalized Content
```bash
curl -X POST http://localhost:8080/v1/interactions/user/test_user/generate-next
```

## Project Structure

```
socialmedia-poc/
├── src/
│   ├── main/
│   │   ├── java/com/example/socialmedia_poc/
│   │   │   ├── controller/
│   │   │   │   ├── FluxController.java
│   │   │   │   ├── SeedController.java
│   │   │   │   ├── InteractionController.java
│   │   │   │   └── HelloController.java
│   │   │   ├── service/
│   │   │   │   ├── OllamaService.java
│   │   │   │   ├── SeedGenerationService.java
│   │   │   │   ├── InteractionService.java
│   │   │   │   └── PersonalizedSeedService.java
│   │   │   ├── model/
│   │   │   │   ├── Meta.java
│   │   │   │   ├── MetaConfig.java
│   │   │   │   ├── Interaction.java
│   │   │   │   └── SeedWithMeta.java
│   │   │   └── SocialmediaPocApplication.java
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── index.html
│   │       │   ├── css/style.css
│   │       │   └── js/script.js
│   │       ├── meta-configs.json
│   │       ├── meta-template.json
│   │       ├── interactions.json
│   │       ├── next-seeds.json
│   │       ├── application.properties
│   │       └── seeds/
│   └── test/
├── pom.xml
├── README.md
├── API_DOCUMENTATION.md
└── INTERACTION_SYSTEM.md
```

## Technologies

- **Backend**: Spring Boot 3.2.3, Java 17
- **Frontend**: Vanilla JavaScript, HTML5, CSS3
- **LLM**: Ollama (llama3.2)
- **Build**: Maven
- **Storage**: JSON files (interactions, seeds)
- **Fonts**: Inter, Space Grotesk (Google Fonts)

## Future Enhancements

- [ ] Database integration (PostgreSQL/MongoDB)
- [ ] Real-time WebSocket updates
- [ ] User authentication and profiles
- [ ] Social features (share, comment)
- [ ] Content moderation
- [ ] A/B testing framework
- [ ] Advanced analytics dashboard
- [ ] Mobile apps (iOS/Android)
- [ ] Collaborative filtering
- [ ] Multi-language support

## Contributing

This is a proof-of-concept project. Contributions are welcome!

## License

MIT License

## Acknowledgments

- Spring Boot team for the excellent framework
- Ollama team for local LLM capabilities
- The open-source community

---

**Flux** - Where every scroll tells your story.
