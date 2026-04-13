document.addEventListener('DOMContentLoaded', () => {
    // ══════════════════════════════════
    // Auth check
    // ══════════════════════════════════
    const sessionToken = localStorage.getItem('flux_session_token');
    const storedUserId = localStorage.getItem('flux_user_id');
    
    if (!sessionToken || !storedUserId) {
        window.location.href = '/login.html';
        return;
    }
    
    fetch(`/v1/users/session/${sessionToken}`)
        .then(response => response.json())
        .then(data => {
            if (data.status !== 'success') {
                localStorage.removeItem('flux_session_token');
                localStorage.removeItem('flux_user_id');
                localStorage.removeItem('flux_user');
                window.location.href = '/login.html';
                return;
            }
            localStorage.setItem('flux_user', JSON.stringify(data.user));
            if (!data.user.onboarded) {
                window.location.href = '/onboard.html';
                return;
            }
            updateUserProfile(data.user);
        })
        .catch(() => { window.location.href = '/login.html'; });
    
    // ══════════════════════════════════
    // DOM refs
    // ══════════════════════════════════
    const wall = document.querySelector('#feedWall');
    const loadingIndicator = document.querySelector('#loadingIndicator');
    const refreshBtn = document.querySelector('#refreshBtn');
    const generateSeedsBtn = document.querySelector('#generateSeedsBtn');
    const scrollTopBtn = document.querySelector('#scrollTopBtn');
    const totalViewedEl = document.querySelector('#totalViewed');
    const avgDwellEl = document.querySelector('#avgDwell');
    const themeToggle = document.querySelector('#themeToggle');
    const soundToggle = document.querySelector('#soundToggle');
    const poolGenToggle = document.querySelector('#poolGenerationToggle');

    const userId = storedUserId;

    // ══════════════════════════════════
    // Theme System
    // ══════════════════════════════════
    const savedTheme = localStorage.getItem('flux_theme') || 'light';
    if (savedTheme === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
    }

    if (themeToggle) {
        themeToggle.addEventListener('click', () => {
            const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
            const newTheme = isDark ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', newTheme);
            localStorage.setItem('flux_theme', newTheme);
        });
    }

    // ══════════════════════════════════
    // Sound System
    // ══════════════════════════════════
    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    let soundEnabled = localStorage.getItem('flux_sound') !== 'off';

    function updateSoundButton() {
        if (soundToggle) {
            soundToggle.innerHTML = soundEnabled ? '&#128266;' : '&#128264;';
            soundToggle.classList.toggle('muted', !soundEnabled);
        }
    }
    updateSoundButton();

    if (soundToggle) {
        soundToggle.addEventListener('click', () => {
            soundEnabled = !soundEnabled;
            localStorage.setItem('flux_sound', soundEnabled ? 'on' : 'off');
            updateSoundButton();
            if (soundEnabled) playTapSound();
        });
    }

    function playTapSound() {
        if (!soundEnabled) return;
        try {
            const osc = audioCtx.createOscillator();
            const gain = audioCtx.createGain();
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            osc.type = 'sine';
            osc.frequency.setValueAtTime(600, audioCtx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(400, audioCtx.currentTime + 0.08);
            gain.gain.setValueAtTime(0.08, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.15);
            osc.start(audioCtx.currentTime);
            osc.stop(audioCtx.currentTime + 0.15);
        } catch (e) { /* ignore audio errors */ }
    }

    function playLikeSound() {
        if (!soundEnabled) return;
        try {
            const osc = audioCtx.createOscillator();
            const gain = audioCtx.createGain();
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            osc.type = 'sine';
            osc.frequency.setValueAtTime(500, audioCtx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(900, audioCtx.currentTime + 0.12);
            gain.gain.setValueAtTime(0.1, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.2);
            osc.start(audioCtx.currentTime);
            osc.stop(audioCtx.currentTime + 0.2);
        } catch (e) { /* ignore */ }
    }

    // ══════════════════════════════════
    // Pool Generation Toggle
    // ══════════════════════════════════
    let poolGenerationEnabled = localStorage.getItem('flux_pool_gen') !== 'off';

    function updatePoolToggle() {
        if (poolGenToggle) {
            poolGenToggle.classList.toggle('active', poolGenerationEnabled);
        }
    }
    updatePoolToggle();

    if (poolGenToggle) {
        poolGenToggle.addEventListener('click', () => {
            poolGenerationEnabled = !poolGenerationEnabled;
            localStorage.setItem('flux_pool_gen', poolGenerationEnabled ? 'on' : 'off');
            updatePoolToggle();
            playTapSound();
        });
    }

    // ══════════════════════════════════
    // Auth helper
    // ══════════════════════════════════
    function authFetch(url, options = {}) {
        if (!options.headers) options.headers = {};
        options.headers['Authorization'] = 'Bearer ' + sessionToken;
        return fetch(url, options);
    }
    
    // ══════════════════════════════════
    // State
    // ══════════════════════════════════
    let allSeeds = [];
    let cardsRendered = 0;
    const cardsPerPage = 9;
    let isLoading = false;
    let totalViewed = 0;
    let totalDwellTime = 0;
    let cardDwellTimes = new Map();
    let personalizedSeedsLoaded = false;
    let isLoadingPersonalized = false;

    // ══════════════════════════════════
    // User profile
    // ══════════════════════════════════
    function updateUserProfile(user) {
        const profileBtn = document.querySelector('.profile-btn');
        if (profileBtn && user.display_name) {
            const displayNameEl = profileBtn.querySelector('span');
            if (displayNameEl) displayNameEl.textContent = user.display_name;
            const avatar = profileBtn.querySelector('.avatar span');
            if (avatar) avatar.textContent = user.display_name.charAt(0).toUpperCase();
        }
    }
    
    // Logout
    const profileBtn = document.querySelector('.profile-btn');
    if (profileBtn) {
        profileBtn.addEventListener('click', () => {
            if (confirm('Do you want to logout?')) {
                authFetch('/v1/users/logout', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ session_token: sessionToken })
                }).catch(() => {});
                localStorage.removeItem('flux_session_token');
                localStorage.removeItem('flux_user_id');
                localStorage.removeItem('flux_user');
                window.location.href = '/login.html';
            }
        });
    }

    // ══════════════════════════════════
    // Interactions
    // ══════════════════════════════════
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
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(interaction)
            });

            if (response.ok) {
                const data = await response.json();
                if (data.trigger && data.trigger !== 'NONE' && poolGenerationEnabled) {
                    showTriggerToast(data.trigger, data.trigger_message || 'New content being prepared...');
                }
            }
        } catch (error) {
            console.error('Failed to record interaction:', error);
        }
    }

    function showTriggerToast(trigger, message) {
        const existing = document.querySelector('.trigger-toast');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.className = 'trigger-toast';
        const icon = trigger === 'DEEP_INTEREST' ? '&#127919;' : trigger === 'ENGAGEMENT_DROP' ? '&#128260;' : '&#127754;';
        toast.innerHTML = `<div class="toast-icon">${icon}</div><div class="toast-message">${message}</div>`;
        document.body.appendChild(toast);

        requestAnimationFrame(() => toast.classList.add('visible'));
        setTimeout(() => {
            toast.classList.remove('visible');
            setTimeout(() => toast.remove(), 400);
        }, 4000);
    }

    // Stats
    const updateStats = () => {
        totalViewedEl.textContent = totalViewed;
        const avgDwell = totalViewed > 0 ? (totalDwellTime / totalViewed).toFixed(1) : 0;
        avgDwellEl.textContent = `${avgDwell}s`;
    };

    // ══════════════════════════════════
    // Observers (animation + dwell tracking)
    // ══════════════════════════════════
    const initializeObserver = () => {
        const cards = document.querySelectorAll('.card:not(.in-view)');
        if (cards.length === 0) return;

        const observer = new IntersectionObserver((entries, obs) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('in-view');
                    obs.unobserve(entry.target);
                }
            });
        }, { root: null, rootMargin: '0px', threshold: 0.1 });
        cards.forEach(card => observer.observe(card));
    };

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
                    
                    let interactionType = 'VIEW';
                    if (dwellSeconds > 8) interactionType = 'LONG_READ';
                    else if (dwellSeconds < 2) interactionType = 'SKIP';
                    
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

    // ══════════════════════════════════
    // Card Creation (no AI Generated tag)
    // ══════════════════════════════════
    function escapeHtmlCard(str) {
        if (!str) return '';
        const d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

    function escapeTag(str) {
        return escapeHtmlCard(str).replace(/[^a-zA-Z0-9 &;]/g, '');
    }

    // Ripple effect on card click
    function addRipple(card, e) {
        const ripple = document.createElement('span');
        ripple.className = 'ripple';
        const rect = card.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height);
        ripple.style.width = ripple.style.height = size + 'px';
        ripple.style.left = (e.clientX - rect.left - size / 2) + 'px';
        ripple.style.top = (e.clientY - rect.top - size / 2) + 'px';
        card.appendChild(ripple);
        setTimeout(() => ripple.remove(), 600);
    }

    const createCard = (seedData, index) => {
        const card = document.createElement('div');
        card.classList.add('card');
        card.style.animationDelay = `${index * 0.08}s`;
        card.dataset.seedId = seedData.seedId;
        card.dataset.category = seedData.category || 'General';

        let content = seedData.content || '';
        if (typeof seedData === 'string') content = seedData;

        const fullText = content.trim().split('\n').filter(l => l.length > 0).join(' ');
        const wordCount = fullText.split(/\s+/).length;
        const readMin = Math.max(1, Math.round(wordCount / 200));

        // Caption: use provided caption, or generate a headline-style caption
        let caption = seedData.caption || '';
        if (!caption) {
            // Generate headline from content: extract key phrase
            const words = fullText.split(/\s+/);
            // Try to find a strong opening clause (before first comma/dash/colon)
            const clauseMatch = fullText.match(/^([^,:\-—]+)/); 
            if (clauseMatch && clauseMatch[1].split(/\s+/).length >= 3 && clauseMatch[1].split(/\s+/).length <= 12) {
                caption = clauseMatch[1].trim();
            } else {
                // Take first 6-8 impactful words
                caption = words.slice(0, Math.min(7, words.length)).join(' ');
            }
            // Clean trailing punctuation and add ellipsis if truncated
            caption = caption.replace(/[.,;:!?]+$/, '');
            if (words.length > 7 && !clauseMatch) caption += '...';
        }

        const tags = (seedData.tags || []).slice(0, 2);
        const tagHtml = tags.map(t => `<span class="tag-pill">${escapeTag(t)}</span>`).join('');

        // Category badge with data attribute for color
        const catName = seedData.category || 'General';

        card.innerHTML = `
            <div class="card-header">
                <span class="category-badge" data-cat="${escapeHtmlCard(catName)}">${escapeHtmlCard(catName)}</span>
                <div class="card-actions">
                    <button class="deepdive-btn" data-seed-id="${seedData.seedId}" title="Deep dive into this topic">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <circle cx="11" cy="11" r="8"/>
                            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                            <line x1="11" y1="8" x2="11" y2="14"/>
                            <line x1="8" y1="11" x2="14" y2="11"/>
                        </svg>
                    </button>
                    <button class="like-btn" data-seed-id="${seedData.seedId}">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                        </svg>
                    </button>
                </div>
            </div>
            <h2 class="card-caption">${escapeHtmlCard(caption)}</h2>
            <div class="card-body">${escapeHtmlCard(fullText)}</div>
            <div class="card-footer">
                <span class="read-time">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    ${readMin} min read
                </span>
                <span class="tag-list">${tagHtml}</span>
            </div>
        `;

        // Sound + ripple on card click/touch
        card.addEventListener('click', (e) => {
            if (e.target.closest('.like-btn')) return;
            playTapSound();
            addRipple(card, e);
        });
        
        // Like button
        const likeBtn = card.querySelector('.like-btn');
        likeBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            likeBtn.classList.toggle('liked');
            if (likeBtn.classList.contains('liked')) {
                playLikeSound();
                recordInteraction(seedData.seedId, seedData.category, 'LIKE');
            }
        });

        // Deep Dive button
        const deepDiveBtn = card.querySelector('.deepdive-btn');
        deepDiveBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            playTapSound();
            showDeepDiveConfirmation(seedData);
        });
        
        trackDwellTime(card, seedData);
        return card;
    };

    // ══════════════════════════════════
    // Deep Dive Confirmation
    // ══════════════════════════════════
    function showDeepDiveConfirmation(seedData) {
        // Remove existing modal if any
        const existing = document.querySelector('.deepdive-modal-overlay');
        if (existing) existing.remove();

        const overlay = document.createElement('div');
        overlay.className = 'deepdive-modal-overlay';
        
        const catName = seedData.category || 'General';
        const preview = (seedData.content || '').substring(0, 120) + (seedData.content && seedData.content.length > 120 ? '...' : '');

        overlay.innerHTML = `
            <div class="deepdive-modal">
                <div class="deepdive-modal-icon">
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                        <circle cx="11" cy="11" r="8"/>
                        <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                        <line x1="11" y1="8" x2="11" y2="14"/>
                        <line x1="8" y1="11" x2="14" y2="11"/>
                    </svg>
                </div>
                <h3 class="deepdive-modal-title">Deep Dive</h3>
                <p class="deepdive-modal-text">Explore this <strong>${escapeHtmlCard(catName)}</strong> topic in depth with AI-researched analysis, key points, and source links.</p>
                <p class="deepdive-modal-preview">"${escapeHtmlCard(preview)}"</p>
                <div class="deepdive-modal-actions">
                    <button class="deepdive-cancel-btn">Cancel</button>
                    <button class="deepdive-confirm-btn">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <circle cx="11" cy="11" r="8"/>
                            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                        </svg>
                        Explore
                    </button>
                </div>
            </div>
        `;

        document.body.appendChild(overlay);
        requestAnimationFrame(() => overlay.classList.add('visible'));

        // Cancel
        overlay.querySelector('.deepdive-cancel-btn').addEventListener('click', () => {
            overlay.classList.remove('visible');
            setTimeout(() => overlay.remove(), 300);
        });
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                overlay.classList.remove('visible');
                setTimeout(() => overlay.remove(), 300);
            }
        });

        // Confirm — navigate to deep dive page
        overlay.querySelector('.deepdive-confirm-btn').addEventListener('click', () => {
            const params = new URLSearchParams({
                postId: seedData.seedId,
                category: catName
            });
            window.location.href = `/deepdive.html?${params.toString()}`;
        });
    }

    // ══════════════════════════════════
    // Rendering
    // ══════════════════════════════════
    const renderCards = () => {
        isLoading = true;
        loadingIndicator?.classList.add('active');
        
        const fragment = document.createDocumentFragment();
        const seedsToRender = allSeeds.slice(cardsRendered, cardsRendered + cardsPerPage);

        seedsToRender.forEach((seedData, index) => {
            if (seedData && (seedData.content || typeof seedData === 'string')) {
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

    const normalizePost = (post) => ({
        seedId: post.post_id || post.seedId || post.postId || ('seed_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
        content: post.content || '',
        caption: post.caption || null,
        category: post.category || 'General',
        tags: post.tags || [],
        metaConfig: post.meta_config || post.metaConfig || null,
        source: post.source || 'SEED',
        batch: post.batch || 1
    });

    // ══════════════════════════════════
    // Fetch & load wall
    // ══════════════════════════════════
    const fetchAllSeeds = async () => {
        try {
            const skeletons = wall.querySelectorAll('.skeleton-card');
            skeletons.forEach(s => s.remove());

            const response = await authFetch(`/v1/wall/${userId}`);
            if (response.ok) {
                const data = await response.json();
                const posts = data.posts || [];
                allSeeds = posts.map(normalizePost);
            }

            if (!allSeeds || allSeeds.length === 0) {
                const fallback = await authFetch('/v1/seeds/with-meta?limit=15');
                if (fallback.ok) allSeeds = await fallback.json();
            }

            if (!allSeeds || allSeeds.length === 0) {
                wall.innerHTML = '<p class="error-message">No stories found. Generate some seeds using the button in the right panel!</p>';
                return;
            }

            wall.innerHTML = '';
            cardsRendered = 0;
            personalizedSeedsLoaded = false;
            isLoadingPersonalized = false;
            renderCards();

        } catch (error) {
            console.error('Failed to load wall:', error);
            wall.innerHTML = '<p class="error-message">Failed to load your wall. Is the backend running?</p>';
        }
    };

    const generateSeeds = async () => {
        if (!generateSeedsBtn) return;
        const originalText = generateSeedsBtn.innerHTML;
        generateSeedsBtn.innerHTML = '<div class="spinner"></div> Generating...';
        generateSeedsBtn.disabled = true;

        try {
            const response = await authFetch('/v1/seeds/generate', { method: 'POST' });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            await response.text();
            setTimeout(() => fetchAllSeeds(), 500);
        } catch (error) {
            console.error('Failed to generate seeds:', error);
            alert('Failed to generate seeds.');
        } finally {
            setTimeout(() => {
                generateSeedsBtn.innerHTML = originalText;
                generateSeedsBtn.disabled = false;
            }, 1000);
        }
    };

    const fetchPersonalizedSeeds = async () => {
        if (isLoadingPersonalized || personalizedSeedsLoaded || !poolGenerationEnabled) return;
        isLoadingPersonalized = true;
        
        try {
            const response = await authFetch(`/v1/wall/${userId}/next`, { method: 'POST' });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            
            const data = await response.json();
            const newPosts = (data.posts || []).map(normalizePost);
            
            if (newPosts.length > 0) {
                const separator = document.createElement('div');
                separator.className = 'personalized-separator';
                separator.innerHTML = `
                    <div class="separator-line"></div>
                    <div class="separator-text">
                        <span class="separator-icon">&#10024;</span>
                        <span>Generated For You (Batch ${data.batch || '?'})</span>
                        <span class="separator-icon">&#10024;</span>
                    </div>
                    <div class="separator-line"></div>
                `;
                wall.appendChild(separator);
                
                allSeeds = allSeeds.concat(newPosts);
                personalizedSeedsLoaded = true;
                renderCards();
            } else {
                personalizedSeedsLoaded = true;
            }
        } catch (error) {
            console.error('Failed to load next wall batch:', error);
        } finally {
            isLoadingPersonalized = false;
        }
    };

    // ══════════════════════════════════
    // Scroll
    // ══════════════════════════════════
    const handleScroll = () => {
        if (isLoading) return;
        const { scrollTop, scrollHeight, clientHeight } = document.documentElement;
        
        if (scrollTopBtn) {
            scrollTopBtn.classList.toggle('visible', scrollTop > 500);
        }
        
        if (scrollTop + clientHeight >= scrollHeight - 100) {
            if (cardsRendered < allSeeds.length) {
                renderCards();
            } else if (!isLoadingPersonalized && poolGenerationEnabled) {
                personalizedSeedsLoaded = false;
                fetchPersonalizedSeeds();
            }
        }
    };

    const scrollToTop = () => window.scrollTo({ top: 0, behavior: 'smooth' });

    // ══════════════════════════════════
    // Nav buttons
    // ══════════════════════════════════
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            playTapSound();
        });
    });

    // View toggle
    document.querySelectorAll('.view-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            playTapSound();
            wall.style.gridTemplateColumns = btn.dataset.view === 'list' ? '1fr' : '';
        });
    });

    // ══════════════════════════════════
    // Event listeners
    // ══════════════════════════════════
    if (refreshBtn) refreshBtn.addEventListener('click', () => { playTapSound(); fetchAllSeeds(); });
    if (generateSeedsBtn) generateSeedsBtn.addEventListener('click', () => { playTapSound(); generateSeeds(); });
    if (scrollTopBtn) scrollTopBtn.addEventListener('click', scrollToTop);
    
    window.addEventListener('scroll', handleScroll, { passive: true });
    
    // Initial load
    fetchAllSeeds();

    // Keyboard shortcuts
    document.addEventListener('keydown', (e) => {
        if (e.key === 'r' && !e.ctrlKey && !e.metaKey) {
            const el = document.activeElement;
            if (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA') {
                e.preventDefault();
                fetchAllSeeds();
            }
        }
        if (e.key === 'Escape') scrollToTop();
    });
});
