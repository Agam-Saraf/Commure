import { useEffect, useState } from 'react'
import './App.css'
import {
  beginLogin,
  completeLoginIfNeeded,
  getAccessToken,
  logoutLocal,
  oauthEnabled,
} from './oauthPkce'
import VideoUploader from './video/VideoUploader'

function App() {
  const [message, setMessage] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [secureMe, setSecureMe] = useState(null)
  const [oauthError, setOauthError] = useState(null)
  const [token, setToken] = useState(() => getAccessToken())

  useEffect(() => {
    let cancelled = false

    async function loadHello() {
      setLoading(true)
      setError(null)

      try {
        if (oauthEnabled()) {
          try {
            const access = await completeLoginIfNeeded()
            if (!cancelled && access) {
              setToken(access)
            }
          } catch (oauthErr) {
            if (!cancelled) {
              setOauthError(
                oauthErr instanceof Error ? oauthErr.message : 'OAuth login failed',
              )
            }
          }
        }

        const response = await fetch('/api/hello')
        if (!response.ok) {
          throw new Error(`Request failed with status ${response.status}`)
        }
        const data = await response.json()
        if (!cancelled) {
          setMessage(data.message)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to reach backend')
          setMessage(null)
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    loadHello()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!oauthEnabled() || !token) {
      return
    }
    let cancelled = false
    async function loadSecure() {
      try {
        const response = await fetch('/api/secure/me', {
          headers: { Authorization: `Bearer ${token}` },
        })
        if (!response.ok) {
          throw new Error(`Secure call failed (${response.status})`)
        }
        const data = await response.json()
        if (!cancelled) {
          setSecureMe(data)
          setOauthError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setSecureMe(null)
          setOauthError(err instanceof Error ? err.message : 'Secure call failed')
        }
      }
    }
    loadSecure()
    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <section id="center">
      <h1>Commure</h1>
      {loading && <p className="status">Loading…</p>}
      {!loading && error && <p className="status error">Error: {error}</p>}
      {!loading && !error && message && (
        <p className="status success">{message}</p>
      )}

      <VideoUploader />

      {oauthEnabled() && (
        <div className="oauth-panel">
          {!token && (
            <button type="button" className="oauth-button" onClick={() => beginLogin()}>
              Sign in with Keycloak
            </button>
          )}
          {token && (
            <button
              type="button"
              className="oauth-button"
              onClick={() => {
                logoutLocal()
                setToken(null)
                setSecureMe(null)
              }}
            >
              Sign out
            </button>
          )}
          {oauthError && <p className="status error">{oauthError}</p>}
          {token && secureMe && (
            <p className="status success">
              Signed in as {secureMe.preferredUsername || secureMe.subject}
            </p>
          )}
        </div>
      )}
    </section>
  )
}

export default App
