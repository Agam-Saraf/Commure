export const MAX_UPLOAD_BYTES = 50_000_000
export const MAX_DURATION_SECONDS = 120
export const MAX_LONG_SIDE = 1920
export const MAX_SHORT_SIDE = 1080
export const ACCEPT = 'video/mp4,.mp4'

// Browsers report durations like 120.007s for a nominal 2-minute video.
const DURATION_TOLERANCE_SECONDS = 0.1

export function formatMegabytes(bytes) {
  return `${(bytes / 1_000_000).toFixed(1)} MB`
}

export function formatDuration(seconds) {
  const whole = Math.round(seconds)
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`
}

export function isMp4(file) {
  return file.type === 'video/mp4' || (!file.type && /\.mp4$/i.test(file.name))
}

/** Returns a user-facing error message, or null when the file passes. */
export function validateFile(file) {
  if (!file) {
    return 'Please choose a video file.'
  }
  if (!isMp4(file)) {
    return file.type.startsWith('video/')
      ? 'Only MP4 videos are supported. Please convert this video to MP4 and try again.'
      : 'Only video files can be uploaded. Please choose an MP4 video.'
  }
  if (file.size === 0) {
    return 'This file is empty. Please choose another video.'
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return `Videos must be 50 MB or smaller. This one is ${formatMegabytes(file.size)}.`
  }
  return null
}

/** Returns a user-facing error message, or null when the metadata passes. */
export function validateMetadata({ duration, width, height }) {
  if (Number.isFinite(duration) && duration > MAX_DURATION_SECONDS + DURATION_TOLERANCE_SECONDS) {
    return `Videos must be 2 minutes or shorter. This one is ${formatDuration(duration)}.`
  }
  const longSide = Math.max(width, height)
  const shortSide = Math.min(width, height)
  if (longSide > MAX_LONG_SIDE || shortSide > MAX_SHORT_SIDE) {
    return `Videos can be at most 1920x1080. This one is ${width}x${height}.`
  }
  return null
}

export function readVideoMetadata(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    const cleanup = () => {
      video.onloadedmetadata = null
      video.onerror = null
      URL.revokeObjectURL(url)
    }
    video.preload = 'metadata'
    video.muted = true
    video.onloadedmetadata = () => {
      const metadata = {
        duration: video.duration,
        width: video.videoWidth,
        height: video.videoHeight,
      }
      cleanup()
      resolve(metadata)
    }
    video.onerror = () => {
      cleanup()
      reject(new Error('Unreadable video'))
    }
    video.src = url
  })
}
