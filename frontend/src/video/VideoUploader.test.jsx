import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { deferred, jsonResponse, mp4File, stubFetch } from '../test/http'
import VideoUploader from './VideoUploader'
import { readVideoMetadata } from './videoRules'

vi.mock('./videoRules', async (importOriginal) => ({
  ...(await importOriginal()),
  readVideoMetadata: vi.fn(),
}))

const OK_METADATA = { duration: 4.2, width: 1280, height: 720 }
const UPLOAD = /^\/api\/videos$/
const TRANSFORM = /^\/api\/videos\/[^/]+\/transform$/

function uploaded(id = 'vid-1', filename = 'clip.mp4') {
  return {
    id,
    filename,
    sizeBytes: 1_000_000,
    durationSeconds: 4.2,
    segmentCount: 1,
    transformed: false,
    transformedSizeBytes: null,
    requestId: null,
  }
}

function transformed(id = 'vid-1', filename = 'clip.mp4', requestId = 'req-123') {
  return { ...uploaded(id, filename), transformed: true, transformedSizeBytes: 900_000, requestId }
}

function setup(options = {}) {
  const user = userEvent.setup({ applyAccept: false, ...options })
  render(<VideoUploader />)
  return { user, input: screen.getByLabelText('Video file') }
}

async function uploadAndWaitReady(user, input, file = mp4File()) {
  await user.upload(input, file)
  await screen.findByRole('button', { name: 'Convert to grayscale' })
}

beforeEach(() => {
  readVideoMetadata.mockResolvedValue(OK_METADATA)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('VideoUploader upload and preview', () => {
  it('renders an upload button and an MP4-only picker', () => {
    const { input } = setup()

    expect(screen.getByRole('button', { name: 'Upload video' })).toBeEnabled()
    expect(input).toHaveAttribute('type', 'file')
    expect(input).toHaveAttribute('accept', 'video/mp4,.mp4')
    expect(screen.queryByRole('button', { name: 'Convert to grayscale' })).not.toBeInTheDocument()
  })

  it('rejects a non-video file without calling the backend', async () => {
    const fetchMock = stubFetch([])
    const { user, input } = setup()

    await user.upload(input, new File(['hi'], 'notes.txt', { type: 'text/plain' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only video files can be uploaded')
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.queryByTestId('original-preview')).not.toBeInTheDocument()
  })

  it('rejects a non-MP4 video', async () => {
    const { user, input } = setup()

    await user.upload(input, new File(['x'], 'clip.mov', { type: 'video/quicktime' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only MP4 videos are supported')
  })

  it('rejects videos over 50 MB without calling the backend', async () => {
    const fetchMock = stubFetch([])
    const { user, input } = setup()

    await user.upload(input, mp4File('big.mp4', 52_000_000))

    expect(await screen.findByRole('alert')).toHaveTextContent('Videos must be 50 MB or smaller. This one is 52.0 MB.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects videos longer than 2 minutes', async () => {
    readVideoMetadata.mockResolvedValue({ duration: 150, width: 640, height: 360 })
    const { user, input } = setup()

    await user.upload(input, mp4File())

    expect(await screen.findByRole('alert')).toHaveTextContent('Videos must be 2 minutes or shorter. This one is 2:30.')
  })

  it('tells the user that long videos are converted in parts', () => {
    setup()

    expect(screen.getByText(/up to 50 MB, 2 minutes and 1920x1080/)).toHaveTextContent(
      'Videos longer than 10 seconds are converted in parts and joined back in order.',
    )
  })

  it('rejects resolutions above 1080p', async () => {
    readVideoMetadata.mockResolvedValue({ duration: 5, width: 3840, height: 2160 })
    const { user, input } = setup()

    await user.upload(input, mp4File())

    expect(await screen.findByRole('alert')).toHaveTextContent('at most 1920x1080')
  })

  it('rejects a video the browser cannot read', async () => {
    readVideoMetadata.mockRejectedValue(new Error('decode'))
    const { user, input } = setup()

    await user.upload(input, mp4File())

    expect(await screen.findByRole('alert')).toHaveTextContent("couldn't read this video")
  })

  it('shows the preview and an uploading buffer, then stores the upload', async () => {
    const pending = deferred()
    const fetchMock = stubFetch([{ method: 'POST', match: UPLOAD, respond: () => pending.promise }])
    const { user, input } = setup()

    await user.upload(input, mp4File())

    expect(await screen.findByTestId('original-preview')).toHaveAttribute('src', 'blob:mock/clip.mp4')
    expect(screen.getByRole('status')).toHaveTextContent('Uploading...')
    expect(screen.queryByRole('button', { name: 'Convert to grayscale' })).not.toBeInTheDocument()

    pending.resolve(jsonResponse(201, uploaded()))

    expect(await screen.findByText('Uploaded clip.mp4')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    const [, options] = fetchMock.mock.calls[0]
    expect(options.body.get('file').name).toBe('clip.mp4')
  })

  it('shows the backend friendly message when upload fails', async () => {
    stubFetch([
      {
        method: 'POST',
        match: UPLOAD,
        respond: () => jsonResponse(415, { code: 'UNSUPPORTED_TYPE', message: 'Only MP4 videos are supported.' }),
      },
    ])
    const { user, input } = setup()

    await user.upload(input, mp4File())

    expect(await screen.findByRole('alert')).toHaveTextContent('Only MP4 videos are supported.')
    expect(screen.queryByRole('button', { name: /Convert|Retry/ })).not.toBeInTheDocument()
  })

  it('shows a reachability message when the server is down', async () => {
    stubFetch([{ method: 'POST', match: UPLOAD, respond: () => Promise.reject(new TypeError('Failed')) }])
    const { user, input } = setup()

    await user.upload(input, mp4File())

    expect(await screen.findByRole('alert')).toHaveTextContent("can't reach the server")
  })
})

describe('VideoUploader grayscale conversion', () => {
  it('shows a converting buffer, then the grayscale preview next to the original', async () => {
    const pending = deferred()
    const fetchMock = stubFetch([
      { method: 'POST', match: UPLOAD, respond: () => jsonResponse(201, uploaded()) },
      { method: 'POST', match: TRANSFORM, respond: () => pending.promise },
    ])
    const { user, input } = setup()
    await uploadAndWaitReady(user, input)

    await user.click(screen.getByRole('button', { name: 'Convert to grayscale' }))

    expect(screen.getByRole('status')).toHaveTextContent('Converting to grayscale...')
    expect(screen.queryByRole('button', { name: 'Convert to grayscale' })).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/api/videos/vid-1/transform', expect.objectContaining({ method: 'POST' }))

    pending.resolve(jsonResponse(200, transformed()))

    const grayscale = await screen.findByTestId('grayscale-preview')
    expect(grayscale).toHaveAttribute('src', '/api/videos/vid-1/transformed')
    expect(screen.getByTestId('original-preview')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Download grayscale video' })).toHaveAttribute(
      'download',
      'grayscale-clip.mp4',
    )
    expect(screen.getByText('Converted (request ID: req-123)')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('converts a long video in parts and previews the reassembled result', async () => {
    readVideoMetadata.mockResolvedValue({ duration: 25, width: 1280, height: 720 })
    const pending = deferred()
    const long = { ...uploaded('vid-long', 'long.mp4'), durationSeconds: 25, segmentCount: 3 }
    stubFetch([
      { method: 'POST', match: UPLOAD, respond: () => jsonResponse(201, long) },
      { method: 'POST', match: TRANSFORM, respond: () => pending.promise },
    ])
    const { user, input } = setup()
    await uploadAndWaitReady(user, input, mp4File('long.mp4', 8_000_000))

    await user.click(screen.getByRole('button', { name: 'Convert to grayscale' }))

    expect(screen.getByRole('status')).toHaveTextContent('Converting 3 parts to grayscale...')

    pending.resolve(
      jsonResponse(200, { ...long, transformed: true, transformedSizeBytes: 2_400_000, requestId: 'req-1,req-2,req-3' }),
    )

    expect(await screen.findByTestId('grayscale-preview')).toHaveAttribute('src', '/api/videos/vid-long/transformed')
    expect(screen.getByText('Converted in 3 parts (request IDs: req-1, req-2, req-3)')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it.each([
    [400, 'INVALID_REQUEST', 'The video could not be processed. Please try uploading it again.'],
    [502, 'SERVICE_AUTH_FAILED', 'The transformation service rejected our credentials. Please contact support.'],
    [503, 'SESSION_NOT_LIVE', 'The transformation service is not live right now. Please try again later.'],
    [413, 'LIMIT_EXCEEDED', 'This clip exceeds the limits: 3 MB, 10 seconds, 1920x1080, 600 frames.'],
    [422, 'VIDEO_TOO_LONG', 'Videos must be 2 minutes or shorter.'],
    [422, 'UNSUPPORTED_VIDEO', 'Part 2 of 3: This video is not supported. Please upload an H.264 MP4.'],
    [500, 'PROCESSING_FAILED', "We couldn't split or reassemble this video. Please try another file."],
    [422, 'UNSUPPORTED_VIDEO', 'This video is not supported. Please upload an H.264 MP4.'],
    [502, 'OUTPUT_TOO_LARGE', 'The converted video exceeded the 4 MB limit. Try a shorter clip.'],
    [504, 'TIMEOUT', 'The transformation service could not be reached or took too long to respond. Please try again.'],
    [503, 'NOT_CONFIGURED', 'Video transformation is not configured on the server.'],
    [404, 'NOT_FOUND', 'That video is no longer available. Please upload it again.'],
  ])('shows the friendly message for %i %s with the request id', async (status, code, message) => {
    stubFetch([
      { method: 'POST', match: UPLOAD, respond: () => jsonResponse(201, uploaded()) },
      {
        method: 'POST',
        match: TRANSFORM,
        respond: () => jsonResponse(status, { code, message, requestId: `req-${code}`, retryAfterSeconds: null }),
      },
    ])
    const { user, input } = setup()
    await uploadAndWaitReady(user, input)

    await user.click(screen.getByRole('button', { name: 'Convert to grayscale' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(message)
    expect(alert).toHaveTextContent(`Request ID: req-${code}`)
    expect(screen.getByRole('button', { name: 'Retry conversion' })).toBeEnabled()
    expect(screen.queryByTestId('grayscale-preview')).not.toBeInTheDocument()
  })

  it('falls back to a status-based message when the error body is not JSON', async () => {
    stubFetch([
      { method: 'POST', match: UPLOAD, respond: () => jsonResponse(201, uploaded()) },
      { method: 'POST', match: TRANSFORM, respond: () => new Response('Gateway Timeout', { status: 504 }) },
    ])
    const { user, input } = setup()
    await uploadAndWaitReady(user, input)

    await user.click(screen.getByRole('button', { name: 'Convert to grayscale' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('took too long to respond')
  })

  it('counts down Retry-After before allowing a retry, then succeeds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let calls = 0
    stubFetch([
      { method: 'POST', match: UPLOAD, respond: () => jsonResponse(201, uploaded()) },
      {
        method: 'POST',
        match: TRANSFORM,
        respond: () =>
          ++calls === 1
            ? jsonResponse(
                429,
                { code: 'RATE_LIMITED', message: 'The transformation service is busy. Please try again shortly.' },
                { 'Retry-After': '2', 'X-Request-Id': 'req-429' },
              )
            : jsonResponse(200, transformed()),
      },
    ])
    const { user, input } = setup({ advanceTimers: vi.advanceTimersByTime })
    await uploadAndWaitReady(user, input)

    await user.click(screen.getByRole('button', { name: 'Convert to grayscale' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('busy')
    expect(alert).toHaveTextContent('Request ID: req-429')
    expect(screen.getByRole('button', { name: 'Retry in 2s' })).toBeDisabled()

    await act(() => vi.advanceTimersByTimeAsync(1000))
    expect(screen.getByRole('button', { name: 'Retry in 1s' })).toBeDisabled()

    await act(() => vi.advanceTimersByTimeAsync(1000))
    const retry = screen.getByRole('button', { name: 'Retry conversion' })
    expect(retry).toBeEnabled()

    await user.click(retry)

    expect(await screen.findByTestId('grayscale-preview')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(calls).toBe(2)
  })
})

describe('VideoUploader out-of-order responses', () => {
  it('ignores a late upload response for a previously selected file', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first, second]
    stubFetch([{ method: 'POST', match: UPLOAD, respond: () => queue.shift().promise }])
    const { user, input } = setup()

    await user.upload(input, mp4File('a.mp4'))
    await screen.findByText('Uploading...')
    await user.upload(input, mp4File('b.mp4'))
    await screen.findByTestId('original-preview')

    second.resolve(jsonResponse(201, uploaded('vid-b', 'b.mp4')))
    expect(await screen.findByText('Uploaded b.mp4')).toBeInTheDocument()

    first.resolve(jsonResponse(201, uploaded('vid-a', 'a.mp4')))
    await act(async () => {})

    expect(screen.getByText('Uploaded b.mp4')).toBeInTheDocument()
    expect(screen.queryByText('Uploaded a.mp4')).not.toBeInTheDocument()
    expect(screen.getByTestId('original-preview')).toHaveAttribute('src', 'blob:mock/b.mp4')
  })

  it('ignores a late transform response after a new file is chosen', async () => {
    const lateTransform = deferred()
    const uploads = [uploaded('vid-a', 'a.mp4'), uploaded('vid-b', 'b.mp4')]
    stubFetch([
      { method: 'POST', match: UPLOAD, respond: () => jsonResponse(201, uploads.shift()) },
      { method: 'POST', match: TRANSFORM, respond: () => lateTransform.promise },
    ])
    const { user, input } = setup()
    await uploadAndWaitReady(user, input, mp4File('a.mp4'))
    await user.click(screen.getByRole('button', { name: 'Convert to grayscale' }))
    expect(screen.getByRole('status')).toHaveTextContent('Converting to grayscale...')

    await uploadAndWaitReady(user, input, mp4File('b.mp4'))
    lateTransform.resolve(jsonResponse(200, transformed('vid-a', 'a.mp4')))
    await act(async () => {})

    expect(screen.queryByTestId('grayscale-preview')).not.toBeInTheDocument()
    expect(screen.getByText('Uploaded b.mp4')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Convert to grayscale' })).toBeEnabled()
  })

  it('ignores a late transform error after a new file is chosen', async () => {
    const lateTransform = deferred()
    const uploads = [uploaded('vid-a', 'a.mp4'), uploaded('vid-b', 'b.mp4')]
    stubFetch([
      { method: 'POST', match: UPLOAD, respond: () => jsonResponse(201, uploads.shift()) },
      { method: 'POST', match: TRANSFORM, respond: () => lateTransform.promise },
    ])
    const { user, input } = setup()
    await uploadAndWaitReady(user, input, mp4File('a.mp4'))
    await user.click(screen.getByRole('button', { name: 'Convert to grayscale' }))

    await uploadAndWaitReady(user, input, mp4File('b.mp4'))
    lateTransform.resolve(jsonResponse(503, { code: 'SERVICE_UNAVAILABLE', message: 'late failure' }))
    await act(async () => {})

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('Uploaded b.mp4')).toBeInTheDocument()
  })
})
