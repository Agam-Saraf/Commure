import { useEffect, useRef, useState } from 'react'
import { transformedUrl, transformVideo, uploadVideo } from './videoApi'
import { ACCEPT, readVideoMetadata, validateFile, validateMetadata } from './videoRules'

const BUSY_LABELS = {
  validating: 'Checking video...',
  uploading: 'Uploading...',
  transforming: 'Converting to grayscale...',
}

function busyLabel(status, video) {
  const parts = video?.segmentCount ?? 1
  return status === 'transforming' && parts > 1
    ? `Converting ${parts} parts to grayscale...`
    : BUSY_LABELS[status]
}

function VideoUploader() {
  const inputRef = useRef(null)
  const selectionRef = useRef(0)
  const abortRef = useRef(null)
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [video, setVideo] = useState(null)
  const [retryIn, setRetryIn] = useState(0)

  useEffect(() => () => abortRef.current?.abort(), [])

  useEffect(() => {
    if (!previewUrl) {
      return undefined
    }
    return () => URL.revokeObjectURL(previewUrl)
  }, [previewUrl])

  useEffect(() => {
    if (retryIn <= 0) {
      return undefined
    }
    const timer = setTimeout(() => setRetryIn((seconds) => seconds - 1), 1000)
    return () => clearTimeout(timer)
  }, [retryIn])

  const busy = status in BUSY_LABELS

  /**
   * Starts a new unit of work and returns a guard: responses can arrive out of order, so only the
   * latest selection or transform may update state.
   */
  function begin() {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const selection = ++selectionRef.current
    return { signal: controller.signal, isCurrent: () => selection === selectionRef.current }
  }

  function fail(err) {
    setStatus('error')
    setError(err)
    setRetryIn(err.retryAfterSeconds ?? 0)
  }

  async function handleFileChange(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }

    const { signal, isCurrent } = begin()
    setError(null)
    setRetryIn(0)
    setVideo(null)
    setPreviewUrl(null)

    const fileError = validateFile(file)
    if (fileError) {
      fail({ message: fileError })
      return
    }

    setStatus('validating')
    let metadataError
    try {
      metadataError = validateMetadata(await readVideoMetadata(file))
    } catch {
      metadataError = "We couldn't read this video. Please upload an H.264 MP4."
    }
    if (!isCurrent()) {
      return
    }
    if (metadataError) {
      fail({ message: metadataError })
      return
    }

    setPreviewUrl(URL.createObjectURL(file))
    setStatus('uploading')
    try {
      const uploaded = await uploadVideo(file, { signal })
      if (!isCurrent()) {
        return
      }
      setVideo(uploaded)
      setStatus('ready')
    } catch (err) {
      if (isCurrent() && err?.name !== 'AbortError') {
        fail(err)
      }
    }
  }

  async function handleTransform() {
    if (!video) {
      return
    }
    const { signal, isCurrent } = begin()
    setError(null)
    setRetryIn(0)
    setStatus('transforming')
    try {
      const result = await transformVideo(video.id, { signal })
      if (!isCurrent()) {
        return
      }
      setVideo(result)
      setStatus('done')
    } catch (err) {
      if (isCurrent() && err?.name !== 'AbortError') {
        fail(err)
      }
    }
  }

  const canTransform = video && !video.transformed && (status === 'ready' || status === 'error')
  const grayscaleUrl = status === 'done' && video?.transformed ? transformedUrl(video.id) : null

  return (
    <div className="video-uploader">
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        aria-label="Video file"
        className="visually-hidden"
        onChange={handleFileChange}
      />
      <div className="actions">
        <button type="button" className="upload-button" onClick={() => inputRef.current?.click()}>
          {previewUrl ? 'Choose another video' : 'Upload video'}
        </button>
        {canTransform && (
          <button
            type="button"
            className="upload-button primary"
            onClick={handleTransform}
            disabled={retryIn > 0}
          >
            {status === 'error'
              ? retryIn > 0
                ? `Retry in ${retryIn}s`
                : 'Retry conversion'
              : 'Convert to grayscale'}
          </button>
        )}
      </div>
      <p className="hint">
        MP4 only, up to 50 MB, 2 minutes and 1920x1080. Videos longer than 10 seconds are converted in
        parts and joined back in order.
      </p>

      {error && (
        <div className="error-banner" role="alert">
          <p>{error.message}</p>
          {error.requestId && <p className="request-id">Request ID: {error.requestId}</p>}
        </div>
      )}

      {previewUrl && (
        <div className="preview-grid">
          <figure className="preview">
            <figcaption>Original</figcaption>
            <div className="preview-frame">
              <video src={previewUrl} controls muted data-testid="original-preview" />
              {busy && (
                <div className="buffer" role="status">
                  <span className="spinner" aria-hidden="true" />
                  {busyLabel(status, video)}
                </div>
              )}
            </div>
          </figure>
          {grayscaleUrl && (
            <figure className="preview">
              <figcaption>Grayscale</figcaption>
              <div className="preview-frame">
                <video key={grayscaleUrl} src={grayscaleUrl} controls muted data-testid="grayscale-preview" />
              </div>
              <a className="download-link" href={grayscaleUrl} download={`grayscale-${video.filename}`}>
                Download grayscale video
              </a>
            </figure>
          )}
        </div>
      )}

      {!previewUrl && status === 'validating' && (
        <div className="buffer inline" role="status">
          <span className="spinner" aria-hidden="true" />
          {BUSY_LABELS.validating}
        </div>
      )}

      {status === 'ready' && video && <p className="status success">Uploaded {video.filename}</p>}
      {status === 'done' && video?.requestId && (
        <p className="request-id-note">
          {video.segmentCount > 1
            ? `Converted in ${video.segmentCount} parts (request IDs: ${video.requestId.split(',').join(', ')})`
            : `Converted (request ID: ${video.requestId})`}
        </p>
      )}
    </div>
  )
}

export default VideoUploader
