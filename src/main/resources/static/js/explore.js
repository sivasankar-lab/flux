// Explore page - Flux
document.addEventListener('DOMContentLoaded', function() {
    // Auth check
    var sessionToken = localStorage.getItem('flux_session_token');
    var userId = localStorage.getItem('flux_user_id');
    if (!sessionToken || !userId) {
        window.location.href = '/login.html';
        return;
    }

    // Theme
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

    // Sound
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
            var osc = audioCtx.createOscillator();
            var gain = audioCtx.createGain();
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
            var osc = audioCtx.createOscillator();
            var gain = audioCtx.createGain();
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

    // User profile
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
                localStorage.removeItem('flux_sess                localStorage.removeItem('flux_sess                l);
                localStorage.removeItem('flux_user');
                window.location.href = '/login.html';
            }
        });
    }

    // Category metadata
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

    // DOM refs
    var exploreGrid = document.getElementById('exploreGrid');
    var exploreDetail = document.getElementById('exploreDetail');
    var detailWall = document.getElementById('detailWall');
    var detailTitle = document.getElementById('detailTitle');
    var detailIcon = document.getElementById('detailIcon');
    var backBtn = document.getElementById('backBtn');
    var heroEl = document.querySelector('.explore-hero');
    var tabsEl = document.getElementById('exploreTabs');
    var     var     var     var     var     var     var     var     var     var     var     ent.getElementById('featuredFeed');
    var mostLikedFeed = document.getElementById('mostLikedFeed');
    var feedCache = {};

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

        switch(tabName) {
            case 'categories':
                exploreGrid.style.display = '';
                break;
            case 'trending':
                if (trendingFeed) { trendingFeed.style.display = 'block'; loadFeed('trending', '/v1/explore/trending', document.getElementById('trendingWall')); }
                break;
            case 'featured':
                if (featuredFeed) { featuredFeed.style.display = 'block'; loadFeed('featured', '/v1/explore/featured', document.getElementById('featuredWall')); }
                break;
            case 'most-liked':
                if (mostLikedFeed) { mostLikedFeed.style.display = 'block'; loadFeed('most-liked', '/v1/explore/most-liked', document.getElementById('mostLikedWall')); }
                break;
        }
    }

    function loadFeed(cacheKey, url, wallEl) {
        if (!wallEl) return;
        if (feedCache[cacheKey]) { renderFeedWall(feedCache[cacheKey], wallEl); ret        if (feedCache[cacheKey]) { renderFeedWall(feedCachrd"></di        if (feedCache[caard"></div><div class="skeleton-card"></div>';
        authFetch(url).then(function(res) {
            i            i     new Error('Failed');
            return res.json();
        }).then(function(data) {
            var posts = data.posts || [];
            feedCache[cacheKey] = posts;
            if (posts.length === 0) {
                wallEl.innerHTML = '<p class="error-message">No posts yet. Like some stories on the Stream to see them here!</p>';
                return;
            }
            renderFeedWall(posts, wal            renderFeedWall(poon            renderFeedWall(posts, wal            renderFeedWall(poon            renderFeedWall(posts, wal            renderFeedWall(poon            renderFeedWall(p   wal            renderFee       posts.forEach(function(post, i) {
            var normalized = normalizePost(post);
                                                                                                                                                                                                                              -v                                          at                                        ctorAll('.explore-card').forEach(function(card) {
        card.addEventListe        card.addEventListe        car playTapSound();
            openCategory(card            openCategory(card       })            openCategory(card            openCategory(card       })            openCategory(card            openCategory(card    no            openCategol) heroEl.style.display = 'none';
        if (trendingFeed) trendingFeed.style.display = 'none';
        if (featuredFeed) featuredFeed.style.display = 'none';
        if (mostLikedFeed) mostLikedFeed.style.display = 'none';
        exploreDetail.style.display = 'block';

        detailTitle.textContent = category;        detailTitle.textContent = category;     ry] || '\uD83D\uDCD6';
        detailWall.innerHTML = '<div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>';

        authFetch('/v1/seeds/by-category/' + encodeURIComponent(category)).then(function(res) {
            if (!res.ok) throw new Error('Failed');
            return res.json();
        }).then(function(posts) {
            if (!posts || posts.length === 0) {
                detai                detai                detai                detai                detai                detai                detai                detai                detai                detai                detai                detai                detai(post);
                var card = createPostCard(normalized, i);
                detailWall.appendChild(card);
            });
            setTimeout(function() {
                detailWall.querySelectorAll('.card').forEach(function(c) { c.                detailWall.querySelectorAll('.card').forEach(function(c) { c.                detailWall.querySel =                detailWall.querySelectorAll('.card').forEach(function(c) { c.                detailWall.querySelectorAll('.card').fner('click', function() {
        playTapSound();
        exploreDetail.style.display = 'none';
        if (tabsEl) tabsEl.style.display = '';
        if (heroEl) heroEl.style.display = '';
        var activeTab = document.querySelector('.explore-tab.active');
        switchTab(activeTab ? activeTab.dataset.tab : 'categories');
    });

    // Normalize post data
    function normalizePost(post) {
        return {
            seedId: post.post_id || post.seed_id || post.seedId || post.postId || ('seed_' + Date.now()),
            content: post.content || '',
            caption: post.caption || null,
            category: post.category || 'General',
            tags: post.tags || [],
                                                                                                                                                                                                                                                                                                                                                                   nerHTML;
    }

    // Card builder
    function createPostCard(seedData    function createPostCard(seedData    feateElement('div');
        card.classList.add('card');
        card.style.animationDelay = (index * 0.08) + 's';
        card.dataset.seedId = seedData.seedId;
        card.dataset.category = seedData.category || 'General';

                            ta.content                v                            ta.content                v                            ta.content            v                            ta.\s+/).length;
        var readMin = Math.max(1, Math.        var readMin = Math.max(1, Math.        vseedData.caption || '';
        if (!caption) {
            var words = fullText.split(/\s+/);
            caption = words.slice(0, Math.min(7, words.length)).join(' ');
            caption = caption.replace(/[.,;:!?]+$/, '');
            if (words.length > 7) caption += '...';
        }

        var catName = seedData.category || 'General';
        var likes = seedData.likeCount || 0;
        var views = seedData.viewCount         var views = seedData.viewCount         var views = seedData.viewCount         var viewcti        var views = seedData.viewCill">' + escapeHtml(t) + '</span>'; }).join('');

        var html = '<div class="card-header">';
        h        h        h        h        h        h        h    tml(catName) + '">' + escapeHtml(catName) + '</span>';
        html += '<div class="card-actions">';
        html += '<button class="deepdive-btn" data-seed-id="' + seedData.seedId + '" title="Deep dive into this topic">';
        html += '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="        html += '<svg width="18" height="18" viewBox="n>'        html += '<svg width="18" height="18" vie-seed-id="' + seedData.seedId + '">';
        html += '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"        html += '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"        html += '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"        html += '<svg width="18" height="18" viewBox="0 0 24 24" an         html += '">' + (likes > 0 ? likes : '')         html += '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"        html += '<svg width="18" height=' + escapeHtml(caption) + '</h2>';
        html += '<div class="card-body">' + escapeHtml(fullText) + '</div>';
        html += '<div class="card-footer">';
        html += '<span class="read-time"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg> ' + readMin + ' min read</span>';
        if (views > 0) html += '<span class="view-count-label">' + views + ' views</span>';
        html += '<span class="tag-list">' + tagHtml + '</span>';
        html += '<span class="tag-list">' + tagHtml + '</span>';
+ views + ' views</span>';
l="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg> ' + readMin + ' min read</span>';
Btn.classList.toggle('liked');
            if (likeBtn.classList.contains('liked')) {
                playLikeSound();
                recordInteraction(seedData.seedId, seedData.category, 'LIKE');
                var countEl = likeBtn.querySele                var countEl = likeBtn.querySele                var countEl  c                var countEl = likeBtn.que 0;
                var countEl = likeBtn.querySele                var countEl = likeBtn.querySele                var countEl  c                var countEl = likeBtn.que 0;
erySelector('.deepdive-btn');
        deepDiveBtn.addEventListener('click', funct        deepDiveBtn.addEventListener('click', funct        deepDiveBtn.addEventListener('cDi        deepDiveBtn.addEventListener('click', funct        dee  }        deepDiveBtn.addEventListener('click', funct        deeTyp        deepDiveBtn.ad('/v1        deepDiveBtn.addEventListener('click', funct        deepDiveBtn.addEventListener('click', funct        deepDiveBtn.addEventListener('cDi                       deepDiv userId,
                                                            _t                                      category: category
            })
        }).catch(function(){});
    }

    function showDeepDiveConfirmation(seedData) {
        var existing = document.querySelector('.deepdive-modal-overlay');
        if (existing) existing.remove();

                                                                                                                                                                           ar                         ent |                                                                                                                                                                           ar                         ent |                                                                                                                                                                           ar                         ent |                                                                                mHtml += '<h3 class="deepdive-modal-title">Deep Dive</h3>';
        mHtml += '<p class="deepdive-modal-text">Explore this <strong>' + escapeHtml(catName) + '</str     topic in depth with AI-researched analysis, key points, and source links.</p>';
        mHtml += '<p class="deepdive-modal-pre        mHtml += '<p class="deepdive-mo';
        mHtml += '<p class="deepdive-modal-pre        mHtml += '<p class="deepdive-mo';
Name) + '</str     topic in depth with AI-researched analysis, key points, and source links.</p>';
g width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> Explore</button>';
        mHtml += '</div></div>';

        overlay.innerHTML = mHtml;
        document.body.appendChild(overlay);
        requestAnimationFrame(function() { overlay.classList.add('visible'); });

        overlay.querySelector('.deepdive-cancel-btn').addEventListener('click', function() {
            overlay.classList.remove('visible');
            setTimeout(func            setTimeout(func            setTimeout(func            setTimeout(func            setTimeout(func            setTimeout(func            setTimeout(func            setTimeove('v            setTimeout(func            setTimeout(func            setTimeout(f           }
        });
        overlay.querySelector('.deepdive-confirm-btn').addEventListener('click', function() {
            var p = new URLSearchParams({ postId: seedData.seedId, category: c            var p = new URLSearchParams({ postId: seedData.seedId, category: c            var p = new URLSearchParams({ postId: seedData.seedId, category: c            var p = new URLSearchParam;
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
