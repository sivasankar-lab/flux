// Explore page — Flux
document.addEventListener('DOMContentLoaded', () => {
    // Auth check
    const sessionToken = localStorage.getItem('flux_session_token');
    const userId = localStorage.getItem('flux_user_id');
    if (!sessionToken || !userId) {
        window.location.href = '/login.html';
        return;
    }

    // ── Theme ──
    const themeToggle = document.getElementById('themeToggle');
    const savedTheme = localStorage.getItem('flux_theme') || 'light';
    if (savedTheme === 'dark') document.documentElement.setAttribute('data-theme', 'dark');

    if (themeToggle) {
        themeToggle.addEventListener('click', () => {
            const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
            document.documentElement.setAttribute('data-theme', isDark ? 'light' : 'dark');
            localStorage.setItem('flux_theme', isDark ? 'light' : 'dark');
        });
    }

    // ── Sound ──
    let soundEnabled = localStorage.getItem('flux_sound') !== 'off';
    const soundToggle = document.getElementById('soundToggle');
    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();

    if (soundToggle) {
        soundToggle.classList.toggle('muted', !soundEnabled);
        soundToggle.addEventListener('click', () => {
            soundEnabled = !soundEnabled;
            localStorage.setItem('flux_sound', soundEnabled ? 'on' : 'off');
            soundToggle.classList.toggle('muted', !soundEnabled);
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
            osc.frequency.exponentialRampToValueAtTime(400, audioCtx.currentTime + 0.1);
            gain.gain.setValueAtTime(0.08, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.15);
            osc.start(audioCtx.currentTime);
            osc.stop(audioCtx.currentTime + 0.15);
        } catch (e) { /* ignore */ }
    }

    // ── Auth helper ──
    function authFetch(url, options = {}) {
        if (!options.headers) options.headers = {};
        options.headers['Authorization'] = 'Bearer ' + sessionToken;
        return fetch(url, options);
    }

    // ── User profile ──
    const user = JSON.parse(localStorage.getItem('flux_user') || '{}');
    const profileBtn = document.querySelector('.profile-btn');
    if (profileBtn && user.display_name) {
        const nameEl = profileBtn.querySelector('span');
        if (nameEl) nameEl.textContent = user.display_name;
        const avatar = profileBtn.querySelector('.avatar span');
        if (avatar) avatar.textContent = user.display_name.charAt(0).toUpperCase();
    }
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

    // ── Category metadata ──
    const CATEGORIES = [
        { key: 'Hooks', icon: '🎮', color: '#6366f1' },
        { key: 'History & Society', icon: '🏛', color: '#d97706' },
        { key: 'Science & How Things Work', icon: '🔬', color: '#059669' },
        { key: 'Psychology & Human Behavior', icon: '🧠', color: '#db2777' },
        { key: 'Technology & Future', icon: '🚀', color: '#2563eb' },
        { key: 'Philosophy & Life Questions', icon: '🤔', color: '#7c3aed' },
        { key: 'Health & Lifestyle Tips', icon: '💚', color: '#dc2626' }
    ];

    const iconMap = {};
    CATEGORIES.forEach(c => iconMap[c.key] = c.icon);

    // ── Fetch category counts ──
    async function loadCategoryCounts() {
        for (const cat of CATEGORIES) {
            try {
                const res = await authFetch(`/v1/seeds/by-category/${encodeURIComponent(cat.key)}`);
                if (res.ok) {
                    const posts = await res.json();
                    const el = document.querySelector(`.count-num[data-cat="${cat.key}"]`);
                    if (el) el.textContent = posts.length;
                }
            } catch (e) { /* ignore */ }
        }
    }
    loadCategoryCounts();

    // ── Grid / Detail refs ──
    const exploreGrid = document.getElementById('exploreGrid');
    const exploreDetail = document.getElementById('exploreDetail');
    const detailWall = document.getElementById('detailWall');
    const detailTitle = document.getElementById('detailTitle');
    const detailIcon = document.getElementById('detailIcon');
    const backBtn = document.getElementById('backBtn');

    // ── Card click → open category ──
    document.querySelectorAll('.explore-card').forEach(card => {
        card.addEventListener('click', () => {
            playTapSound();
            const category = card.dataset.category;
            openCategory(category);
        });
    });

    async function openCategory(category) {
        // Hide grid, show detail
        exploreGrid.style.display = 'none';
        document.querySelector('.explore-hero').style.display = 'none';
        exploreDetail.style.display = 'block';

        detailTitle.textContent = category;
        detailIcon.textContent = iconMap[category] || '📖';
        detailWall.innerHTML = '<div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>';

        try {
            const res = await authFetch(`/v1/seeds/by-category/${encodeURIComponent(category)}`);
            if (!res.ok) throw new Error('Failed');
            const posts = await res.json();

            if (!posts || posts.length === 0) {
                detailWall.innerHTML = '<p class="error-message">No stories in this category yet. Head to Stream and generate seeds!</p>';
                return;
            }

            detailWall.innerHTML = '';
            posts.forEach((post, i) => {
                const card = createPostCard(post, i);
                detailWall.appendChild(card);
            });
            // Animate cards in
            setTimeout(() => {
                detailWall.querySelectorAll('.card').forEach(c => c.classList.add('in-view'));
            }, 50);
        } catch (e) {
            detailWall.innerHTML = '<p class="error-message">Failed to load stories. Try again.</p>';
        }
    }

    // ── Back button ──
    backBtn.addEventListener('click', () => {
        playTapSound();
        exploreDetail.style.display = 'none';
        exploreGrid.style.display = '';
        document.querySelector('.explore-hero').style.display = '';
    });

    // ── Card builder (simplified version) ──
    function escapeHtml(str) {
        if (!str) return '';
        const d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

    function createPostCard(seedData, index) {
        const card = document.createElement('div');
        card.classList.add('card');
        card.style.animationDelay = `${index * 0.08}s`;

        let content = seedData.content || '';
        const fullText = content.trim().split('\n').filter(l => l.length > 0).join(' ');
        const wordCount = fullText.split(/\s+/).length;
        const readMin = Math.max(1, Math.round(wordCount / 200));

        let caption = seedData.caption || '';
        if (!caption) {
            const words = fullText.split(/\s+/);
            const clauseMatch = fullText.match(/^([^,:\-\u2014]+)/);
            if (clauseMatch && clauseMatch[1].split(/\s+/).length >= 3 && clauseMatch[1].split(/\s+/).length <= 12) {
                caption = clauseMatch[1].trim();
            } else {
                caption = words.slice(0, Math.min(7, words.length)).join(' ');
            }
            caption = caption.replace(/[.,;:!?]+$/, '');
            if (words.length > 7 && !clauseMatch) caption += '...';
        }

        const catName = seedData.category || 'General';
        const tags = (seedData.tags || []).slice(0, 2);
        const tagHtml = tags.map(t => `<span class="tag-pill">${escapeHtml(t)}</span>`).join('');

        card.innerHTML = `
            <div class="card-header">
                <span class="category-badge" data-cat="${escapeHtml(catName)}">${escapeHtml(catName)}</span>
            </div>
            <h2 class="card-caption">${escapeHtml(caption)}</h2>
            <div class="card-body">${escapeHtml(fullText)}</div>
            <div class="card-footer">
                <span class="read-time">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                    ${readMin} min read
                </span>
                <span class="tag-list">${tagHtml}</span>
            </div>
        `;
        return card;
    }

    // ── Check for URL param (direct link to category) ──
    const params = new URLSearchParams(window.location.search);
    const directCat = params.get('category');
    if (directCat) {
        openCategory(directCat);
    }
});
