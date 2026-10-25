const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://127.0.0.1:8081'
const REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'commure'
const CLIENT_ID = import.meta.env.VITE_OAUTH_CLIENT_ID || 'commure-spa'

function base64UrlEncode(buffer) {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  bytes.forEach((b) => {
    binary += String.fromCharCode(b)
  })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

async function sha256(plain) {
  const data = new TextEncoder().encode(plain)
  return crypto.subtle.digest('SHA-256', data)
}

export function oauthEnabled() {
  return import.meta.env.VITE_OAUTH_ENABLED === 'true'
}

export async function beginLogin() {
  const verifier = base64UrlEncode(crypto.getRandomValues(new Uint8Array(32)))
  const challenge = base64UrlEncode(await sha256(verifier))
  const state = base64UrlEncode(crypto.getRandomValues(new Uint8Array(16)))
  sessionStorage.setItem('pkce_verifier', verifier)
  sessionStorage.setItem('pkce_state', state)

  const redirectUri = `${window.location.origin}/`
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    response_type: 'code',
    scope: 'openid profile email',
    redirect_uri: redirectUri,
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state,
  })
  window.location.assign(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth?${params}`,
  )
}

export async function completeLoginIfNeeded() {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const state = params.get('state')
  if (!code) {
    return null
  }
  const expected = sessionStorage.getItem('pkce_state')
  const verifier = sessionStorage.getItem('pkce_verifier')
  if (!verifier || !state || state !== expected) {
    throw new Error('PKCE state/verifier mismatch')
  }
  const redirectUri = `${window.location.origin}/`
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: CLIENT_ID,
    code,
    redirect_uri: redirectUri,
    code_verifier: verifier,
  })
  const tokenRes = await fetch(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    },
  )
  if (!tokenRes.ok) {
    throw new Error(`Token exchange failed (${tokenRes.status})`)
  }
  const tokens = await tokenRes.json()
  sessionStorage.setItem('access_token', tokens.access_token)
  sessionStorage.removeItem('pkce_verifier')
  sessionStorage.removeItem('pkce_state')
  window.history.replaceState({}, document.title, window.location.pathname)
  return tokens.access_token
}

export function getAccessToken() {
  return sessionStorage.getItem('access_token')
}

export function logoutLocal() {
  sessionStorage.removeItem('access_token')
}
