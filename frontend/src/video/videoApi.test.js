import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse } from '../test/http'
import { ApiRequestError, toFriendlyError, transformedUrl, transformVideo, uploadVideo } from './videoApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('toFriendlyError', () => {
  it('prefers the backend ApiError body', async () => {
    const err = await toFriendlyError(
      jsonResponse(429, { code: 'RATE_LIMITED', message: 'Busy.', requestId: 'req-1', retryAfterSeconds: 4 }),
    )

    expect(err).toBeInstanceOf(ApiRequestError)
    expect(err).toMatchObject({ message: 'Busy.', status: 429, code: 'RATE_LIMITED', requestId: 'req-1', retryAfterSeconds: 4 })
  })

  it('falls back to headers and status messages for non-JSON bodies', async () => {
    const err = await toFriendlyError(
      new Response('upstream down', { status: 503, headers: { 'Retry-After': '9', 'X-Request-Id': 'req-h' } }),
    )

    expect(err.message).toMatch(/temporarily unavailable/)
    expect(err.retryAfterSeconds).toBe(9)
    expect(err.requestId).toBe('req-h')
  })

  it('uses a generic message for unknown statuses', async () => {
    const err = await toFriendlyError(new Response('', { status: 418 }))

    expect(err.message).toBe('Something went wrong. Please try again.')
    expect(err.retryAfterSeconds).toBeNull()
  })
})

describe('requests', () => {
  it('uploads as multipart with a video/mp4 part even when the browser left the type empty', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse(201, { id: 'v1' })))
    vi.stubGlobal('fetch', fetchMock)

    await uploadVideo(new File(['ftyp'], 'clip.mp4', { type: '' }))

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/videos')
    expect(options.method).toBe('POST')
    const part = options.body.get('file')
    expect(part.type).toBe('video/mp4')
    expect(part.name).toBe('clip.mp4')
  })

  it('throws a friendly error on network failure but rethrows aborts', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    await expect(transformVideo('v1')).rejects.toMatchObject({ message: expect.stringMatching(/can't reach/) })

    const abort = new DOMException('aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(abort)))
    await expect(transformVideo('v1')).rejects.toBe(abort)
  })

  it('encodes ids in URLs', () => {
    expect(transformedUrl('a/b')).toBe('/api/videos/a%2Fb/transformed')
  })
})
