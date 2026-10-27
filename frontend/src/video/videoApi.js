const UNREACHABLE = "We can't reach the server right now. Please try again in a moment."

const FALLBACK_MESSAGES = {
  400: 'The video could not be processed. Please try uploading it again.',
  404: 'That video is no longer available. Please upload it again.',
  413: 'This video exceeds the limits: 50 MB, 2 minutes and 1920x1080.',
  415: 'Only MP4 videos are supported.',
  422: 'This video is not supported. Please upload an H.264 MP4.',
  429: 'The transformation service is busy. Please try again shortly.',
  500: UNREACHABLE,
  502: UNREACHABLE,
  503: 'The transformation service is temporarily unavailable. Please try again shortly.',
  504: 'The transformation service took too long to respond. Please try again.',
}

export class ApiRequestError extends Error {
  constructor(message, { status = 0, code = null, requestId = null, retryAfterSeconds = null } = {}) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
    this.requestId = requestId
    this.retryAfterSeconds = retryAfterSeconds
  }
}

function parseRetryAfter(value) {
  const seconds = Number.parseInt(value ?? '', 10)
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : null
}

export async function toFriendlyError(response) {
  let body = null
  try {
    body = await response.json()
  } catch {
    // Non-JSON bodies (e.g. dev proxy errors) fall back to the status mapping.
  }
  const message =
    body?.message || FALLBACK_MESSAGES[response.status] || 'Something went wrong. Please try again.'
  return new ApiRequestError(message, {
    status: response.status,
    code: body?.code ?? null,
    requestId: body?.requestId ?? response.headers.get('X-Request-Id'),
    retryAfterSeconds: body?.retryAfterSeconds ?? parseRetryAfter(response.headers.get('Retry-After')),
  })
}

async function request(url, options) {
  let response
  try {
    response = await fetch(url, options)
  } catch (err) {
    if (err?.name === 'AbortError') {
      throw err
    }
    throw new ApiRequestError(UNREACHABLE)
  }
  if (!response.ok) {
    throw await toFriendlyError(response)
  }
  return response.json()
}

export function uploadVideo(file, { signal } = {}) {
  const form = new FormData()
  // Some OSes leave File.type empty for .mp4; the backend requires video/mp4.
  const clip = file.type === 'video/mp4' ? file : new Blob([file], { type: 'video/mp4' })
  form.append('file', clip, file.name)
  return request('/api/videos', { method: 'POST', body: form, signal })
}

export function transformVideo(id, { signal } = {}) {
  return request(`/api/videos/${encodeURIComponent(id)}/transform`, { method: 'POST', signal })
}

export function transformedUrl(id) {
  return `/api/videos/${encodeURIComponent(id)}/transformed`
}
