// Explore page - Flux
document.addEventListener('DOMContentLoaded', function() {
    var sessionToken = localStorage.getItem('flux_session_token');
    var userId = localStorage.getItem('flux_user_id');
    if (!sessionToken || !userId) { window.location.href = '/login.html'; return; }

    var themeToggle = document.getElementById('themeToggle');
    var savedTheme = localStorage.getItem('flux_theme') || 'light';
    if (savedTheme === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
    if (themeToggle) {
        themeToggle.addEventListener('click', function() {
            var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
            document.documentElement.setAttribute('data-theme', isDark ? 'light' : 'dark');
            localStorage.setItem('flux_theme', isDark ? 'light' : 'dark');
        });
    }

    var soundEnabled = localStorage.getItem('flux_sound') !== 'off';
    var soundToggle = document.getElementById('soundToggle');
    var audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    if (soundToggle) {
        soundToggle.classList.toggle('muted', !soundEnabled);
        soundToggle.addEventListener('click', function() {
            soundEnabled = !soundEnabled;
            localStorage.setItem('flux_sound', soundEnabled ? 'on' : 'off');
            soundToggle.classList.toggle('muted', !soundEnabled);
        });
    }

    function playTapSound() {
        if (!soundEnabled) return;
        try {
            var osc = audioCtx.createOscillator(), gain = audioCtx.createGain();
            osc.connect(gain); gain.connect(audioCtx.destination);
            osc.type = 'sine';
            osc.frequency.setValueAtTime(600, audioCtx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(400, audioCtx.currentTime + 0.1);
            gain.gain.setValueAtTime(0.08, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.15);
            osc.start(audioCtx.currentTime); osc.stop(audioCtx.currentTime + 0.15);
        } catch(e) {}
    }

    function playLikeSound() {
        if (!soundEnabled) return;
        try {
            var osc = audioCtx.createOscillator(), gain = audioCtx.createGain();
            osc.connect(gain); gain.connect(audioCtx.destination);
            osc.type = 'sine';
            osc.frequency.setValueAtTime(500, audioCtx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(900, audioCtx.currentTime + 0.12);
            gain.gain.setValueAtTime(0.1, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.2);
            osc.start(audioCtx.currentTime); osc.stop(audioCtx.currentTime + 0.2);
        } catch(e) {}
    }

    function authFetch(url, options) {
        options = options || {};
        if (!options.headers) options.headers = {};
        options.headers['Authorization'] = 'Bearer ' + sessionToken;
        return fetch(url, options);
    }

    var user = JSON.parse(localStorage.getItem('flux_user') || '{}');
    var profileBtn = document.querySelector('.profile-btn');
    if (profileBtn && user.display_name) {
        var nameEl = profileBtn.querySelector('span');
        if (nameEl) nameEl.textContent = user.display_name;
        var avatar = profileBtn.querySelector('.avatar span');
        if (avatar) avatar.textContent = user.display_name.charAt(0).toUpperCase();
    }
    if (profileBtn) {
        profileBtn.addEventListener('click', function() {
            if (confirm('Do you want to logout?')) {
                authFetch('/v1/users/logout', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ session_token: sessionToken })
                }).catch(function(){});
                localStorage.removeItem('flux_session_token');
                localStorage.removeItem('flux_user_id');
                localStorage.removeItem('flux_user');
                window.location.href = '/login.html';
            }
        });
    }

    var CATEGORIES = [
        { key: 'Hooks', icon: '\uD83C\uDFAE', color: '#6366f1' },
        { key: 'History & Society', icon: '\uD83C\uDFDB', color: '#d97706' },
        { key: 'Science & How Things Work', icon: '\uD83D\uDD2C', color: '#059669' },
        { key: 'Psychology & Human Behavior', icon: '\uD83E\uDDE0', color: '#db2777' },
        { key: 'Technology & Future', icon: '\uD83D\uDE80', color: '#2563eb' },
        { key: 'Philosophy & Life Questions', icon: '\uD83E\uDD14', color: '#7c3aed' },
        { key: 'Health & Lifestyle Tips', icon: '\uD83D\uDC9A', color: '#dc2626' }
    ];
    var iconMap = {};
    CATEGORIES.forEach(function(c) { iconMap[c.key] = c.icon; });

    var exploreGrid = document.getElementById('exploreGrid');
    var exploreDetail = document.getElementById('exploreDetail');
    var detailWall = document.getElementById('detailWall');
    var detailTitle = document.getElementById('detailTitle');
    var detailIcon = document.getElementById('detailIcon');
    var backBtn = document.getElementById('backBtn');
    var heroEl = document.querySelector('.explore-hero');
    var tabsEl = document.getElementById('exploreTabs');
    var trendingFeed = document.getElementById('trendingFeed');
    var featuredFeed = document.getElementById('featuredFeed');
    var mostLikedFeed = document.getElementById('mostLikedFeed');
    var feedCache = {};

    // Load category counts
    CATEGORIES.forEach(function(cat) {
        authFetch('/v1/seeds/by-category/' + encodeURIComponent(cat.key))
            .then(function(res) { return res.ok ? res.json() : []; })
            .then(function(posts) {
                var el = document.querySelector('.count-num[data-cat="' + cat.key + '"]');
                if (el) el.textContent = posts.length;
            }).catch(function(){});
    });

    // Tab switching
    document.querySelectorAll('.explore-tab').forEach(function(tab) {
        tab.addEventListener('click', function() {
            document.querySelectorAll('.explore-tab').forEach(function(t) { t.classList.remove('active'); });
            tab.classList.add('active');
            playTapSound();
            switchTab(tab.dataset.tab);
        });
    });

    function switchTab(tabName) {
        exploreGrid.style.display = 'none';
        exploreDetail.style.display = 'none';
        if (trendingFeed) trendingFeed.style.display = 'none';
        if (featuredFeed) featuredFeed.style.display = 'none';
        if (mostLikedFeed) mostLikedFeed.style.display = 'none';
        if (tabName === 'categories') {
            exploreGrid.style.display = '';
        } else if (tabName === 'trending' && trendingFeed) {
            trendingFeed.style.display = 'block';
            loadFeed('trending', '/v1/explore/trending', document.getElementById('trendingWall'));
        } else if (tabName === 'featured' && featuredFeed) {
            featuredFeed.style.display = 'block';
            loadFeed('featured', '/v1/explore/featured', document.getElementById('featuredWall'));
        } else if (tabName === 'most-liked' && mostLikedFeed) {
            mostLikedFeed.style.display = 'block';
            loadFeed('most-liked', '/v1/explore/most-liked', document.getElementById('mostLikedWall'));
        }
    }

    function loadFeed(cacheKey, url, wallEl) {
        if (!wallEl) return;
        if (feedCache[cacheKey]) { renderFeedWall(feedCache[cacheKey], wallEl); return; }
        wallEl.innerHTML = '<div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>';
        authFetch(url).then(function(res) {
            if (!res.ok) throw new Error('Failed');
            return res.json();
        }).then(function(data) {
            var posts = data.posts || [];
            feedCache[cacheKey] = posts;
            if (posts.length === 0) {
                wallEl.innerHTML = '<p class="error-message">No posts yet. Like some stories on the Stream to see them here!</p>';
                return;
            }
            renderFeedWall(posts, wallEl);
        }).catch(function() {
            wallEl.innerHTML = '<p class="error-message">Failed to load. Try again.</p>';
        });
    }

    function renderFeedWall(posts, wallEl) {
        wallEl.innerHTML = '';
        posts.forEach(function(post, i) {
            var card = createPostCard(normalizePost(post), i);
            wallEl.appendChild(card);
        });
        setTimeout(function() {
            wallEl.querySelectorAll('.card').forEach(function(c) { c.classList.add('in-view'); });
        }, 50);
    }

    // Category card clicks
    document.querySelectorAll('.explore-card').forEach(function(card) {
        card.addEventListener('click', function() {
            playTapSound();
            openCategory(card.dataset.category);
        });
    });

    function openCategory(category) {
        exploreGrid.style.display = 'none';
        if (tabsEl) tabsEl.style.display = 'none';
        if (heroEl) heroEl.style.display = 'none';
        if (trendingFeed) trendingFeed.style.display = 'none';
        if (featuredFeed) featuredFeed.style.display = 'none';
        if (mostLikedFeed) mostLikedFeed.style.display = 'none';
        exploreDetail.style.display = 'block';
        detailTitle.textContent = category;
        detailIcon.textContent = iconMap[category] || '\uD83D\uDCD6';
        detailWall.innerHTML = '<div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>';

        authFetch('/v1/seeds/by-category/' + encodeURIComponent(category)).then(function(res) {
            if (!res.ok) throw new Error('Failed');
            return res.json();
        }).then(function(posts) {
            if (!posts || posts.length === 0) {
                detailWall.innerHTML = '<p class="error-message">No stories in this category yet.</p>';
                return;
            }
            detailWall.innerHTML = '';
            posts.forEach(function(post, i) {
                detailWall.appendChild(createPostCard(normalizePost(post), i));
            });
            setTimeout(function() {
                detailWall.querySelectorAll('.card').forEach(function(c) { c.classList.add('in-view'); });
            }, 50);
        }).catch(function() {
            detailWall.innerHTML = '<p class="error-message">Failed to load stories. Try again.</p>';
        });
    }

    // Back button
    if (backBtn) {
        backBtn.addEventListener('click', function() {
            playTapSound();
            exploreDetail.style.display = 'none';
            if (tabsEl) tabsEl.style.display = '';
            if (heroEl) heroEl.style.display = '';
            var activeTab = document.querySelector('.explore-tab.active');
            switchTab(activeTab ? activeTab.dataset.tab : 'categories');
        });
    }

    function normalizePost(post) {
        return {
            seedId: post.post_id || post.seed_id || post.seedId || post.postId || ('seed_' + Date.now()),
            content: post.content || '',
            caption: post.caption || null,
            category: post.category || 'General',
            tags: post.tags || [],
            likeCount: post.like_count || post.likeCount || 0,
            viewCount: post.view_count || post.viewCount || 0,
            engagementScore: post.engagement_score || 0
        };
    }

    function escapeHtml(str) {
        if (!str) return '';
        var d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

    function createPostCard(seedData, index) {
        var card = document.createElement('div');
        card.classList.add('card');
        card.style.animationDelay = (index * 0.08) + 's';
        card.dataset.seedId = seedData.seedId;
        card.dataset.category = seedData.category || 'General';

        var content = seedData.content || '';
        var fullText = content.trim().split('\n').filter(function(l) { return l.length > 0; }).join(' ');
        var wordCount = fullText.split(/\s+/).length;
        var readMin = Math.max(1, Math.round(wordCount / 200));

        var caption = seedData.caption || '';
        if (!caption) {
            var words = fullText.split(/\s+/);
            caption = words.slice(0, Math.min(7, words.length)).join(' ');
            caption = caption.replace(/[.,;:!?]+$/, '');
            if (words.length > 7) caption += '...';
        }

        var catName = seedData.category || 'General';
        var likes = seedData.likeCount || 0;
        var views = seedData.viewCount || 0;
        var tags = (seedData.tags || []).slice(0, 2);
        var tagHtml = tags.map(function(t) { return '<span class="tag-pill">' + escapeHtml(t) + '</span>'; }).join('');

        var html = '<div class="card-header">';
        html += '<span class="category-badge" data-cat="' + escapeHtml(catName) + '">' + escapeHtml(catName) + '</span>';
        html += '<div class="card-actions">';
        html += '<button class="deepdive-btn" data-seed-id="' + seedData.seedId + '" title="Deep dive">';
        html += '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>';
        html += '</button>';
        html += '<button class="like-btn" data-seed-id="' + seedData.seedId + '">';
        html += '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>';
        html += '<span class="like-count">' + (likes > 0 ? likes : '') + '</span>';
        html += '</button>';
        html += '</div></div>';
        html += '<h2 class="card-caption">' + escapeHtml(caption) + '</h2>';
        html += '<div class="card-body">' + escapeHtml(fullText) + '</div>';
        html += '<div class="card-footer">';
        html += '<span class="read-time"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg> ' + readMin + ' min read</span>';
        if (views > 0) html += '<span class="view-count-label">' + views + ' views</span>';
        html += '<span class="tag-list">' + tagHtml + '</span>';
        html += '</div>';
        card.innerHTML = html;

        var likeBtn = card.querySelector('.like-btn');
        likeBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            likeBtn.classList.toggle('liked');
            if (likeBtn.classList.contains('liked')) {
                playLikeSound();
                recordInteraction(seedData.seedId, seedData.category, 'LIKE');
                var countEl = likeBtn.querySelector('.like-count');
                if (countEl) {
                    var cur = parseInt(countEl.textContent) || 0;
                    countEl.textContent = cur + 1;
                }
            }
        });

        var deepDiveBtn = card.querySelector('.deepdive-btn');
        deepDiveBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            playTapSound();
            showDeepDiveConfirmation(seedData);
        });

        return card;
    }

    function recordInteraction(seedId, category, interactionType) {
        authFetch('/v1/interactions/record', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ user_id: userId, seed_id: seedId, interaction_type: interactionType, category: category })
        }).catch(function(){});
    }

    function showDeepDiveConfirmation(seedData) {
        var existing = document.querySelector('.deepdive-modal-overlay');
        if (existing) existing.remove();
        var overlay = document.createElement('div');
        overlay.className = 'deepdive-modal-overlay';
        var catName = seedData.category || 'General';
        var preview = (seedData.content || '').substring(0, 120);
        if (seedData.content && seedData.content.length > 120) preview += '...';

        var m = '<div class="deepdive-modal">';
        m += '<div class="deepdive-modal-icon"><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg></div>';
        m += '<h3 class="deepdive-modal-title">Deep Dive</h3>';
        m += '<p class="deepdive-modal-text">Explore this <strong>' + escapeHtml(catName) + '</strong> topic in depth with AI-researched analysis, key points, and source links.</p>';
        m += '<p class="deepdive-modal-preview">"' + escapeHtml(preview) + '"</p>';
        m += '<div class="deepdive-modal-actions">';
        m += '<button class="deepdive-cancel-btn">Cancel</button>';
        m += '<button class="deepdive-confirm-btn"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> Explore</button>';
        m += '</div></div>';
        overlay.innerHTML = m;
        document.body.appendChild(overlay);
        requestAnimationFrame(function() { overlay.classList.add('visible'); });

        overlay.querySelector('.deepdive-cancel-btn').addEventListener('click', function() {
            overlay.classList.remove('visible');
            setTimeout(function() { overlay.remove(); }, 300);
        });
        overlay.addEventListener('click', function(e) {
            if (e.target === overlay) {
                overlay.classList.remove('visible');
                setTimeout(function() { overlay.remove(); }, 300);
            }
        });
        overlay.querySelector('.deepdive-confirm-btn').addEventListener('click', function() {
            var p = new URLSearchParams({ postId: seedData.seedId, category: catName, from: 'explore' });
            window.location.href = '/deepdive.html?' + p.toString();
        });
    }

    // URL params for direct links
    var params = new URLSearchParams(window.location.search);
    var directCat = params.get('category');
    var directTab = params.get('tab');
    if (directCat) {
        openCategory(directCat);
    } else if (directTab) {
        document.querySelectorAll('.explore-tab').forEach(function(t) { t.classList.remove('active'); });
        var target = document.querySelector('.explore-tab[data-tab="' + directTab + '"]');
        if (target) { target.classList.add('active'); switchTab(directTab); }
    }
});
