// Check if user is already logged in
const sessionToken = localStorage.getItem('flux_session_token');
if (sessionToken) {
    // Validate session
    fetch(`/v1/users/session/${sessionToken}`)
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                // Session is valid, redirect to feed
                window.location.href = '/index.html';
            } else {
                // Invalid session, clear it
                localStorage.removeItem('flux_session_token');
                localStorage.removeItem('flux_user_id');
                localStorage.removeItem('flux_user');
            }
        })
        .catch(error => {
            console.error('Session validation error:', error);
        });
}

const loginForm = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');
const switchLink = document.getElementById('switchLink');
const switchText = document.getElementById('switchText');
const errorMsg = document.getElementById('errorMsg');
const successMsg = document.getElementById('successMsg');
const loginBtn = document.getElementById('loginBtn');
const registerBtn = document.getElementById('registerBtn');

let isLoginMode = true;

// Switch between login and register
switchLink.addEventListener('click', () => {
    isLoginMode = !isLoginMode;
    
    if (isLoginMode) {
        loginForm.classList.remove('hidden');
        registerForm.classList.add('hidden');
        switchText.textContent = "Don't have an account? ";
        switchLink.textContent = "Sign up";
    } else {
        loginForm.classList.add('hidden');
        registerForm.classList.remove('hidden');
        switchText.textContent = "Already have an account? ";
        switchLink.textContent = "Login";
    }
    
    hideMessages();
});

// Login form submission
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMessages();
    
    const username = document.getElementById('loginUsername').value.trim();
    
    if (!username) {
        showError('Please enter your username');
        return;
    }
    
    loginBtn.disabled = true;
    loginBtn.textContent = 'Logging in...';
    
    try {
        const response = await fetch('/v1/users/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username })
        });
        
        const data = await response.json();
        
        if (data.status === 'success') {
            // Store session data
            localStorage.setItem('flux_session_token', data.user.session_token);
            localStorage.setItem('flux_user_id', data.user.user_id);
            localStorage.setItem('flux_user', JSON.stringify(data.user));
            
            showSuccess('Login successful! Redirecting...');
            
            // Redirect to feed
            setTimeout(() => {
                window.location.href = '/index.html';
            }, 1000);
        } else {
            showError(data.message || 'Login failed');
            loginBtn.disabled = false;
            loginBtn.textContent = 'Login';
        }
    } catch (error) {
        console.error('Login error:', error);
        showError('Failed to connect to server');
        loginBtn.disabled = false;
        loginBtn.textContent = 'Login';
    }
});

// Register form submission
registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMessages();
    
    const username = document.getElementById('registerUsername').value.trim();
    const email = document.getElementById('registerEmail').value.trim();
    const displayName = document.getElementById('registerDisplayName').value.trim();
    
    if (!username || !email || !displayName) {
        showError('Please fill in all fields');
        return;
    }
    
    registerBtn.disabled = true;
    registerBtn.textContent = 'Creating account...';
    
    try {
        const response = await fetch('/v1/users/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username,
                email,
                display_name: displayName
            })
        });
        
        const data = await response.json();
        
        if (data.status === 'success') {
            // Store session data
            localStorage.setItem('flux_session_token', data.user.session_token);
            localStorage.setItem('flux_user_id', data.user.user_id);
            localStorage.setItem('flux_user', JSON.stringify(data.user));
            
            showSuccess('Account created! Redirecting...');
            
            // Redirect to feed
            setTimeout(() => {
                window.location.href = '/index.html';
            }, 1000);
        } else {
            showError(data.message || 'Registration failed');
            registerBtn.disabled = false;
            registerBtn.textContent = 'Create Account';
        }
    } catch (error) {
        console.error('Registration error:', error);
        showError('Failed to connect to server');
        registerBtn.disabled = false;
        registerBtn.textContent = 'Create Account';
    }
});

function showError(message) {
    errorMsg.textContent = message;
    errorMsg.classList.remove('hidden');
    successMsg.classList.add('hidden');
}

function showSuccess(message) {
    successMsg.textContent = message;
    successMsg.classList.remove('hidden');
    errorMsg.classList.add('hidden');
}

function hideMessages() {
    errorMsg.classList.add('hidden');
    successMsg.classList.add('hidden');
}
