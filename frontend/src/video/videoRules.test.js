import { describe, expect, it } from 'vitest'
import { ACCEPT, MAX_UPLOAD_BYTES, formatDuration, isMp4, validateFile, validateMetadata } from './videoRules'

function fileOf(name, type, size) {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validateFile', () => {
  it('accepts an MP4 within the size limit', () => {
    expect(validateFile(fileOf('clip.mp4', 'video/mp4', 1_000_000))).toBeNull()
  })

  it('accepts videos over the 3 MB per-clip API limit because the server splits them', () => {
    expect(validateFile(fileOf('long.mp4', 'video/mp4', 3_500_000))).toBeNull()
  })

  it('accepts exactly 50,000,000 bytes and rejects one byte more', () => {
    expect(validateFile(fileOf('clip.mp4', 'video/mp4', MAX_UPLOAD_BYTES))).toBeNull()
    expect(validateFile(fileOf('clip.mp4', 'video/mp4', MAX_UPLOAD_BYTES + 1))).toBe(
      'Videos must be 50 MB or smaller. This one is 50.0 MB.',
    )
  })

  it('rejects non-video files', () => {
    expect(validateFile(fileOf('notes.txt', 'text/plain', 10))).toMatch(/Only video files/)
    expect(validateFile(fileOf('photo.png', 'image/png', 10))).toMatch(/Only video files/)
  })

  it('rejects videos that are not MP4', () => {
    expect(validateFile(fileOf('clip.mov', 'video/quicktime', 10))).toMatch(/Only MP4 videos/)
    expect(validateFile(fileOf('clip.webm', 'video/webm', 10))).toMatch(/Only MP4 videos/)
  })

  it('rejects empty and missing files', () => {
    expect(validateFile(fileOf('clip.mp4', 'video/mp4', 0))).toMatch(/empty/)
    expect(validateFile(null)).toMatch(/choose a video/)
  })

  it('treats a typeless .mp4 as MP4', () => {
    expect(isMp4(fileOf('clip.MP4', '', 10))).toBe(true)
    expect(isMp4(fileOf('clip.bin', '', 10))).toBe(false)
  })

  it('restricts the picker to MP4', () => {
    expect(ACCEPT).toBe('video/mp4,.mp4')
  })
})

describe('validateMetadata', () => {
  it('accepts a 10 second 1080p clip', () => {
    expect(validateMetadata({ duration: 10.04, width: 1920, height: 1080 })).toBeNull()
  })

  it('accepts long videos up to 2 minutes', () => {
    expect(validateMetadata({ duration: 45, width: 1280, height: 720 })).toBeNull()
    expect(validateMetadata({ duration: 120.05, width: 1280, height: 720 })).toBeNull()
  })

  it('rejects videos longer than 2 minutes', () => {
    expect(validateMetadata({ duration: 125.4, width: 640, height: 360 })).toBe(
      'Videos must be 2 minutes or shorter. This one is 2:05.',
    )
  })

  it('formats durations as minutes and seconds', () => {
    expect(formatDuration(4.4)).toBe('0:04')
    expect(formatDuration(59.6)).toBe('1:00')
    expect(formatDuration(605)).toBe('10:05')
  })

  it('rejects resolutions above 1920x1080', () => {
    expect(validateMetadata({ duration: 5, width: 3840, height: 2160 })).toMatch(/at most 1920x1080/)
    expect(validateMetadata({ duration: 5, width: 1920, height: 1200 })).toMatch(/at most 1920x1080/)
  })

  it('allows portrait 1080x1920', () => {
    expect(validateMetadata({ duration: 5, width: 1080, height: 1920 })).toBeNull()
  })

  it('ignores an unknown duration and leaves it to the server', () => {
    expect(validateMetadata({ duration: Number.POSITIVE_INFINITY, width: 640, height: 360 })).toBeNull()
  })
})
