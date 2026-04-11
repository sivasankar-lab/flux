document.addEventListener('DOMContentLoaded', () => {
    // Check authentication first
    const sessionToken = localStorage.getItem('flux_session_token');
    const storedUserId = localStorage.getItem('flux_user_id');
    
    if (!sessionToken || !storedUserId) {
        // No session, redirect to login
        window.location.href = '/login.html';
        return;
    }
    
    // Validate session
    fetch(`/v1/users/session/${sessionToken}`)
        .then(response => response.json())
        .then(data => {
            if (data.status !== 'success') {
                // Invalid session, redirect to login
                localStorage.removeItem('flux_session_token');
                localStorage.removeItem('flux_user_id');
                localStorage.removeItem('flux_user');
                window.location.href = '/login.html';
                return;
            }
            
            // Update user info
            localStorage.setItem('flux_user', JSON.stringify(data.user));

            // Redirect to onboarding if not completed
            if (!data.user.onboarded) {
                window.location.href = '/onboard.html';
                return;
            }

            updateUserProfile(data.user);
        })
        .catch(error => {
            console.error('Session validation error:', error);
            window.location.href = '/login.html';
        });
    
    const wall = document.querySelector('#feedWall');
    const loadingIndicator = document.querySelector('#loadingIndicator');
    const refreshBtn = document.querySelector('#refreshBtn');
    const generateSeedsBtn = document.querySelector('#generateSeedsBtn');
    const scrollTopBtn = document.querySelector('#scrollTopBtn');
    const totalViewedEl = document.querySelector('#totalViewed');
    const avgDwellEl = document.querySelector('#avgDwell');
    
    // User ID from authenticated session
    const userId = storedUserId;

    // Authenticated fetch helper — injects Bearer token on every request
    function authFetch(url, options = {}) {
        if (!options.headers) options.headers = {};
        options.headers['Authorization'] = 'Bearer ' + sessionToken;
        return fetch(url, options);
    }
    
    let allSeeds = [];
    let cardsRendered = 0;
    const cardsPerPage = 9;
    let isLoading = false;
    let totalViewed = 0;
    let totalDwellTime = 0;
    let cardDwellTimes = new Map();
    let personalizedSeedsLoaded = false; // Track if generated batch is loaded
    let isLoadingPersonalized = false; // Prevent duplicate generation requests

    // Update user profile in UI
    function updateUserProfile(user) {
        const profileBtn = document.querySelector('.profile-btn');
        if (profileBtn && user.display_name) {
            const displayNameEl = profileBtn.querySelector('span');
            if (displayNameEl) {
                displayNameEl.textContent = user.display_name;
            }
        }
    }
    
    // Logout function
    function logout() {
        const sessionToken = localStorage.getItem('flux_session_token');
        
        if (sessionToken) {
            authFetch('/v1/users/logout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ session_token: sessionToken })
            }).catch(error => console.error('Logout error:', error));
        }
        
        localStorage.removeItem('flux_session_token');
        localStorage.removeItem('flux_user_id');
        localStorage.removeItem('flux_user');
        window.location.href = '/login.html';
    }
    
    // Add logout button handler
    const profileBtn = document.querySelector('.profile-btn');
    if (profileBtn) {
        profileBtn.addEventListener('click', () => {
            if (confirm('Do you want to logout?')) {
                logout();
            }
        });
    }

    // Generate or retrieve user ID (legacy - now using session-based ID)
    function generateUserId() {
        const id = 'user_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('flux_user_id', id);
        return id;
    }

    // Record interaction with backend and handle smart triggers
    async function recordInteraction(seedId, category, interactionType, dwellTimeMs = null, metaData = null) {
        const interaction = {
            user_id: userId,
            seed_id: seedId,
            interaction_type: interactionType,
            dwell_time_ms: dwellTimeMs,
            category: category,
            meta_data: metaData
        };

        try {
            const response = await authFetch('/v1/interactions/record', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(interaction)
            });

            if (response.ok) {
                const data = await response.json();
                // Trigger fired — generation is queued in the background
                if (data.trigger && data.trigger !== 'NONE') {
                    showTriggerToast(data.trigger, data.trigger_message || 'New content being prepared...');
                }
            }
        } catch (error) {
            console.error('Failed to record interaction:', error);
        }
    }

    // Toast notification for triggers
    function showTriggerToast(trigger, message) {
        // Remove existing toast
        const existing = document.querySelector('.trigger-toast');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.className = 'trigger-toast';
        toast.innerHTML = `
            <div class="toast-icon">${trigger === 'DEEP_INTEREST' ? '🎯' : trigger === 'ENGAGEMENT_DROP' ? '🔄' : '🌊'}</div>
            <div class="toast-message">${message}</div>
        `;
        document.body.appendChild(toast);

        // Animate in
        requestAnimationFrame(() => toast.classList.add('visible'));

        // Auto-dismiss after 4s
        setTimeout(() => {
            toast.classList.remove('visible');
            setTimeout(() => toast.remove(), 400);
        }, 4000);
    }

    // Update statistics
    const updateStats = () => {
        totalViewedEl.textContent = totalViewed;
        const avgDwell = totalViewed > 0 ? (totalDwellTime / totalViewed).toFixed(1) : 0;
        avgDwellEl.textContent = `${avgDwell}s`;
    };

    // Initialize Intersection Observer for animations
    const initializeObserver = () => {
        const cards = document.querySelectorAll('.card:not(.in-view)');
        if (cards.length === 0) return;

        const observerOptions = {
            root: null,
            rootMargin: '0px',
            threshold: 0.1
        };

        const observerCallback = (entries, observer) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('in-view');
                    observer.unobserve(entry.target);
                }
            });
        };

        const observer = new IntersectionObserver(observerCallback, observerOptions);
        cards.forEach(card => observer.observe(card));
    };

    // Track card dwell time
    const trackDwellTime = (card, seedData) => {
        const startTime = Date.now();
        cardDwellTimes.set(card, { startTime, seedData });
        
        const dwellObserver = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (!entry.isIntersecting && cardDwellTimes.has(card)) {
                    const endTime = Date.now();
                    const data = cardDwellTimes.get(card);
                    const dwellTime = endTime - data.startTime;
                    const dwellSeconds = dwellTime / 1000;
                    
                    totalDwellTime += dwellSeconds;
                    totalViewed++;
                    updateStats();
                    
                    // Determine interaction type based on dwell time
                    let interactionType = 'VIEW';
                    if (dwellSeconds > 8) {
                        interactionType = 'LONG_READ';
                    } else if (dwellSeconds < 2) {
                        interactionType = 'SKIP';
                    }
                    
                    // Record interaction
                    recordInteraction(
                        data.seedData.seedId,
                        data.seedData.category,
                        interactionType,
                        dwellTime,
                        data.seedData.metaConfig ? {
                            intensity: data.seedData.metaConfig.intensity_range ? 
                                Math.floor((data.seedData.metaConfig.intensity_range[0] + data.seedData.metaConfig.intensity_range[1]) / 2) : null,
                            pacing: data.seedData.metaConfig.pacing
                        } : null
                    );
                    
                    cardDwellTimes.delete(card);
                    dwellObserver.unobserve(card);
                }
            });
        }, { threshold: 0 });
        
        dwellObserver.observe(card);
    };

    // HTML escape for card content
    function escapeHtmlCard(str) {
        if (!str) return '';
        const d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }
    function escapeTag(str) {
        return escapeHtmlCard(str).replace(/[^a-zA-Z0-9 &;]/g, '');
    }

    // Create card element
    const createCard = (seedData, index) => {
        const card = document.createElement('div');
        card.classList.add('card');
        card.style.animationDelay = `${index * 0.1}s`;
        card.dataset.seedId = seedData.seedId;
        card.dataset.category = seedData.category;

        let content = seedData.content || '';
        
        // Handle old format (plain text) vs new format (object)
        if (typeof seedData === 'string') {
            content = seedData;
        }

        // Clean up content — join multi-line into single flowing text
        const fullText = content.trim().split('\n').filter(l => l.length > 0).join(' ');

        // Estimate reading time
        const wordCount = fullText.split(/\s+/).length;
        const readMin = Math.max(1, Math.round(wordCount / 200));

        // Tags (up to 2)
        const tags = (seedData.tags || []).slice(0, 2);
        const tagHtml = tags.map(t => `<span class="tag-pill">${escapeTag(t)}</span>`).join('');

        // Source badge
        const sourceBadge = seedData.source === 'GENERATED' 
            ? `<span class="source-badge generated">AI Generated</span>` 
            : '';

        card.innerHTML = `
            <div class="card-header">
                <span class="category-badge">${seedData.category || 'General'}</span>
                ${sourceBadge}
                <button class="like-btn" data-seed-id="${seedData.seedId}">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                    </svg>
                </button>
            </div>
            <div class="card-body">${escapeHtmlCard(fullText)}</div>
            <div class="card-footer">
                <span class="read-time">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    ${readMin} min read
                </span>
                <span class="tag-list">${tagHtml}</span>
            </div>
        `;
        
        // Add like button handler
        const likeBtn = card.querySelector('.like-btn');
        likeBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            likeBtn.classList.toggle('liked');
            
            if (likeBtn.classList.contains('liked')) {
                recordInteraction(seedData.seedId, seedData.category, 'LIKE');
            }
        });
        
        trackDwellTime(card, seedData);
        return card;
    };

    // Render cards
    const renderCards = () => {
        isLoading = true;
        loadingIndicator?.classList.add('active');
        
        const fragment = document.createDocumentFragment();
        const seedsToRender = allSeeds.slice(cardsRendered, cardsRendered + cardsPerPage);

        seedsToRender.forEach((seedData, index) => {
            if(seedData && (seedData.content || typeof seedData === 'string')) {
                const card = createCard(seedData, index);
                fragment.appendChild(card);
            }
        });

        wall.appendChild(fragment);
        cardsRendered += seedsToRender.length;
        
        setTimeout(() => {
            initializeObserver();
            isLoading = false;
            loadingIndicator?.classList.remove('active');
        }, 100);
    };

    // Normalize wall post data to a consistent shape for rendering
    const normalizePost = (post) => {
        return {
            seedId: post.post_id || post.seedId || post.postId || ('seed_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
            content: post.content || '',
            category: post.category || 'General',
            tags: post.tags || [],
            metaConfig: post.meta_config || post.metaConfig || null,
            source: post.source || 'SEED',
            batch: post.batch || 1
        };
    };

    // Fetch user's wall (initial load)
    const fetchAllSeeds = async () => {
        try {
            // Remove skeleton loaders
            const skeletons = wall.querySelectorAll('.skeleton-card');
            skeletons.forEach(skeleton => skeleton.remove());

            // Load from user's wall API
            const response = await authFetch(`/v1/wall/${userId}`);
            if (response.ok) {
                const data = await response.json();
                const posts = data.posts || [];
                allSeeds = posts.map(normalizePost);
            }

            // Fallback: if wall is empty, try seeds API directly
            if (!allSeeds || allSeeds.length === 0) {
                const fallback = await authFetch('/v1/seeds/with-meta?limit=15');
                if (fallback.ok) {
                    allSeeds = await fallback.json();
                }
            }

            if (!allSeeds || allSeeds.length === 0) {
                wall.innerHTML = '<p class="error-message">No seeds found. Generate some seeds using the button in the right panel!</p>';
                return;
            }

            wall.innerHTML = '';
            cardsRendered = 0;
            personalizedSeedsLoaded = false;
            isLoadingPersonalized = false;
            renderCards();

        } catch (error) {
            console.error('Failed to load wall:', error);
            wall.innerHTML = `<p class="error-message">Failed to load your wall. Is the backend running?</p>`;
        }
    };

    // Generate new seeds
    const generateSeeds = async () => {
        if (!generateSeedsBtn) return;
        
        const originalText = generateSeedsBtn.innerHTML;
        generateSeedsBtn.innerHTML = `
            <div class="spinner"></div>
            Generating...
        `;
        generateSeedsBtn.disabled = true;

        try {
            const response = await authFetch('/v1/seeds/generate', { method: 'POST' });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            await response.text();
            
            // Refresh the feed after generation
            setTimeout(() => {
                fetchAllSeeds();
            }, 500);
            
        } catch (error) {
            console.error('Failed to generate seeds:', error);
            alert('Failed to generate seeds. Please check the console for details.');
        } finally {
            setTimeout(() => {
                generateSeedsBtn.innerHTML = originalText;
                generateSeedsBtn.disabled = false;
            }, 1000);
        }
    };

    // Fetch next batch of personalized posts for the wall
    const fetchPersonalizedSeeds = async () => {
        if (isLoadingPersonalized || personalizedSeedsLoaded) return;
        isLoadingPersonalized = true;
        
        try {
            // Generate next batch via wall API
            const response = await authFetch(`/v1/wall/${userId}/next`, { method: 'POST' });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            const newPosts = (data.posts || []).map(normalizePost);
            
            if (newPosts.length > 0) {
                // Add a separator before generated posts
                const separator = document.createElement('div');
                separator.className = 'personalized-separator';
                separator.innerHTML = `
                    <div class="separator-line"></div>
                    <div class="separator-text">
                        <span class="separator-icon">✨</span>
                        <span>Generated For You (Batch ${data.batch || '?'})</span>
                        <span class="separator-icon">✨</span>
                    </div>
                    <div class="separator-line"></div>
                `;
                wall.appendChild(separator);
                
                // Add to allSeeds and render
                allSeeds = allSeeds.concat(newPosts);
                personalizedSeedsLoaded = true;
                renderCards();
                
                console.log(`Loaded ${newPosts.length} generated posts (batch ${data.batch})`);
            } else {
                console.log('No personalized posts generated. Keep interacting!');
                personalizedSeedsLoaded = true; // prevent re-triggered empty calls
            }
        } catch (error) {
            console.error('Failed to load next wall batch:', error);
        } finally {
            isLoadingPersonalized = false;
        }
    };

    // Infinite scroll handler
    const handleScroll = () => {
        if (isLoading) return;
        
        const { scrollTop, scrollHeight, clientHeight } = document.documentElement;
        
        // Show/hide scroll to top button
        if (scrollTopBtn) {
            if (scrollTop > 500) {
                scrollTopBtn.classList.add('visible');
            } else {
                scrollTopBtn.classList.remove('visible');
            }
        }
        
        // Load more cards if available
        if (scrollTop + clientHeight >= scrollHeight - 100) {
            if (cardsRendered < allSeeds.length) {
                renderCards();
            } else if (!isLoadingPersonalized) {
                // All rendered, load next generated batch from wall API
                personalizedSeedsLoaded = false; // Allow repeated batches
                fetchPersonalizedSeeds();
            }
        }
    };

    // Scroll to top
    const scrollToTop = () => {
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    };

    // Navigation button handlers
    const navButtons = document.querySelectorAll('.nav-btn');
    navButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            navButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
        });
    });

    // Category chip handlers
    const categoryChips = document.querySelectorAll('.category-chip');
    categoryChips.forEach(chip => {
        chip.addEventListener('click', async () => {
            categoryChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            
            const category = chip.textContent.trim();
            if (category === 'All') {
                fetchAllSeeds();
            } else {
                await fetchSeedsByCategory(category);
            }
        });
    });

    // Fetch seeds by category
    const fetchSeedsByCategory = async (category) => {
        try {
            wall.innerHTML = '<div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>';
            
            const categoryMap = {
                'History': 'History & Society',
                'Science': 'Science & How Things Work',
                'Psychology': 'Psychology & Human Behavior',
                'Technology': 'Technology & Future',
                'Philosophy': 'Philosophy & Life Questions',
                'Health': 'Health & Lifestyle Tips'
            };
            
            const fullCategory = categoryMap[category] || category;
            const response = await authFetch(`/v1/seeds/by-category/${encodeURIComponent(fullCategory)}`);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            allSeeds = await response.json();
            
            if (!allSeeds || allSeeds.length === 0) {
                wall.innerHTML = `<p class="error-message">No seeds found for ${category}. Try generating more seeds!</p>`;
                return;
            }
            
            wall.innerHTML = '';
            cardsRendered = 0;
            personalizedSeedsLoaded = false; // Reset personalized flag
            isLoadingPersonalized = false;
            renderCards();
            
        } catch (error) {
            console.error('Failed to load seeds by category:', error);
            wall.innerHTML = `<p class="error-message">Failed to load ${category} seeds.</p>`;
        }
    };

    // View toggle handlers
    const viewButtons = document.querySelectorAll('.view-btn');
    viewButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            viewButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            
            const view = btn.dataset.view;
            if (view === 'list') {
                wall.style.gridTemplateColumns = '1fr';
            } else {
                wall.style.gridTemplateColumns = '';
            }
        });
    });

    // Event listeners
    if (refreshBtn) {
        refreshBtn.addEventListener('click', fetchAllSeeds);
    }
    
    if (generateSeedsBtn) {
        generateSeedsBtn.addEventListener('click', generateSeeds);
    }
    
    if (scrollTopBtn) {
        scrollTopBtn.addEventListener('click', scrollToTop);
    }
    
    window.addEventListener('scroll', handleScroll, { passive: true });
    
    // Initial load
    fetchAllSeeds();

    // Add keyboard shortcuts
    document.addEventListener('keydown', (e) => {
        // R key to refresh
        if (e.key === 'r' && !e.ctrlKey && !e.metaKey) {
            const activeElement = document.activeElement;
            if (activeElement.tagName !== 'INPUT' && activeElement.tagName !== 'TEXTAREA') {
                e.preventDefault();
                fetchAllSeeds();
            }
        }
        
        // ESC to scroll to top
        if (e.key === 'Escape') {
            scrollToTop();
        }
    });
});