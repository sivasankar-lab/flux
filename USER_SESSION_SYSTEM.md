# User Session System Documentation

## Overview
Flux now includes a complete user session management system with registration, login, and per-user data storage. Each user has their own dedicated JSON files for interactions and personalized feeds.

## User Data Structure

### Users File
**Location**: `src/main/resources/users.json`

Stores all registered users with the following structure:
```json
[
  {
    "user_id": "user_1739693456000_a1b2c3d4",
    "username": "johndoe",
    "email": "john@example.com",
    "display_name": "John Doe",
    "created_at": "2026-02-16T10:30:00Z",
    "last_login": "2026-02-16T11:00:00Z",
    "session_token": "session_1739693456000_uuid123"
  }
]
```

### Per-User Data Directory
**Location**: `src/main/resources/user-data/{user_id}/`

Each user gets a dedicated directory containing:

1. **interactions.json** - User's interaction history
```json
[
  {
    "user_id": "user_1739693456000_a1b2c3d4",
    "seed_id": "seed_tech_1",
    "interaction_type": "LIKE",
    "dwell_time_ms": 8500,
    "category": "Tech & Innovation",
    "timestamp": "2026-02-16T11:05:00Z",
    "meta_data": {
      "intensity": 7,
      "pacing": "Balanced"
    }
  }
]
```

2. **next-seeds.json** - User's personalized feed
```json
[
  {
    "seed_id": "personalized_seed_1",
    "content": "AI-generated content tailored for this user...",
    "category": "Tech & Innovation",
    "meta_config": { ... },
    "generation_context": {
      "based_on_interaction": "user_preference_analysis",
      "user_preference_signal": "high",
      "narrative_depth": "deep"
    }
  }
]
```

## API Endpoints

### User Management

#### Register User
```http
POST /v1/users/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "display_name": "John Doe"
}

Response:
{
  "status": "success",
  "message": "User registered successfully",
  "user": {
    "user_id": "user_...",
    "username": "johndoe",
    "email": "john@example.com",
    "display_name": "John Doe",
    "session_token": "session_..."
  }
}
```

#### Login User
```http
POST /v1/users/login
Content-Type: application/json

{
  "username": "johndoe"
}

Response:
{
  "status": "success",
  "message": "Login successful",
  "user": {
    "user_id": "user_...",
    "session_token": "session_..."
  }
}
```

#### Validate Session
```http
GET /v1/users/session/{sessionToken}

Response:
{
  "status": "success",
  "user": {
    "user_id": "user_...",
    "username": "johndoe",
    "display_name": "John Doe"
  }
}
```

#### Logout User
```http
POST /v1/users/logout
Content-Type: application/json

{
  "session_token": "session_..."
}

Response:
{
  "status": "success",
  "message": "Logout successful"
}
```

#### Get User Profile
```http
GET /v1/users/{userId}

Response:
{
  "status": "success",
  "user": {
    "user_id": "user_...",
    "username": "johndoe",
    "email": "john@example.com",
    "display_name": "John Doe",
    "created_at": "2026-02-16T10:30:00Z"
  }
}
```

#### List All Users
```http
GET /v1/users/all

Response:
{
  "status": "success",
  "count": 5,
  "users": [...]
}
```

## Frontend Flow

### 1. Landing Page (login.html)
- User arrives at `/login.html`
- Can switch between Login and Register forms
- On successful authentication:
  - Session token stored in `localStorage`
  - User ID stored in `localStorage`
  - User object stored in `localStorage`
  - Redirects to `/index.html`

### 2. Feed Page (index.html)
- Checks for valid session token on load
- If no session or invalid session → redirect to login
- Displays user's display name in navigation bar
- Profile button click → logout confirmation
- All interactions tied to authenticated user ID

### 3. Session Management
```javascript
// Session data stored in localStorage:
- flux_session_token: "session_..."
- flux_user_id: "user_..."
- flux_user: JSON object with user details
```

## Benefits of Per-User Storage

### 1. Data Isolation
- Each user's interactions are completely separate
- No mixing of preference data between users
- Privacy by design

### 2. Scalability
- Easy to migrate individual users to database
- Can archive inactive users' JSON files
- Simple backup per user

### 3. Debugging
- Easy to inspect individual user's data
- Clear data structure in file system
- No complex queries needed for development

### 4. Personalization
- Each user gets truly personalized content
- Preferences don't pollute other users' feeds
- Clean slate for new users

## Migration Path

### Current: JSON File Storage
```
user-data/
├── user_1739693456000_a1b2c3d4/
│   ├── interactions.json
│   └── next-seeds.json
├── user_1739693460000_b2c3d4e5/
│   ├── interactions.json
│   └── next-seeds.json
```

### Future: Database Storage
- Easy to read JSON files and insert into database
- User table, Interactions table, PersonalizedSeeds table
- Same API structure, just different data layer
- No changes needed in frontend

## Testing User Sessions

### 1. Register a new user
```bash
curl -X POST http://localhost:8080/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "display_name": "Test User"
  }'
```

### 2. Check user directory was created
```bash
ls -la src/main/resources/user-data/
```

### 3. Login and get session token
```bash
curl -X POST http://localhost:8080/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser"}'
```

### 4. Record an interaction
```bash
curl -X POST http://localhost:8080/v1/interactions/record \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "<user_id_from_login>",
    "seed_id": "test_seed_1",
    "interaction_type": "VIEW",
    "category": "Tech & Innovation",
    "dwell_time_ms": 5000
  }'
```

### 5. Check user's interactions file
```bash
cat src/main/resources/user-data/<user_id>/interactions.json
```

## Security Considerations (For Production)

⚠️ **Current Implementation is POC-level**

For production use, add:
1. **Password hashing** (bcrypt, argon2)
2. **JWT tokens** instead of simple session strings
3. **Token expiration** (sessions should expire)
4. **HTTPS only** for all authentication endpoints
5. **Rate limiting** on login/register endpoints
6. **Email verification** for new accounts
7. **Password reset** flow
8. **Session refresh** mechanism
9. **CSRF protection**
10. **XSS prevention** (already using content security)

## Code Architecture

### Models
- `User.java` - User entity with authentication fields
- `Interaction.java` - Unchanged, uses user_id
- `SeedWithMeta.java` - Unchanged

### Services
- `UserService.java` - User CRUD, session management, directory creation
- `InteractionService.java` - Modified to use per-user files via UserService
- `PersonalizedSeedService.java` - Unchanged, uses InteractionService

### Controllers
- `UserController.java` - NEW: Authentication endpoints
- `InteractionController.java` - Modified to work with per-user storage
- `SeedController.java` - Unchanged

### Frontend
- `login.html` - NEW: Authentication UI
- `auth.js` - NEW: Login/register logic
- `script.js` - Modified: Session validation, logout, uses session user ID
- `index.html` - Modified: Shows display name, profile button for logout
