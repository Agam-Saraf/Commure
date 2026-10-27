import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// jsdom has no object URL support.
URL.createObjectURL = vi.fn((blob) => `blob:mock/${blob?.name ?? 'blob'}`)
URL.revokeObjectURL = vi.fn()

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})
