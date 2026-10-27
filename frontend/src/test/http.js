import { vi } from 'vitest'

export function jsonResponse(status, body, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

/** A promise whose resolution the test controls, to observe in-flight UI states. */
export function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/**
 * Stubs global fetch with handlers matched in order by method + path.
 * Each handler returns a Response or a promise of one.
 */
export function stubFetch(routes) {
  const fetchMock = vi.fn((url, options = {}) => {
    const method = options.method ?? 'GET'
    const route = routes.find((r) => r.method === method && r.match.test(url))
    if (!route) {
      return Promise.reject(new TypeError(`Unexpected request ${method} ${url}`))
    }
    return Promise.resolve(route.respond(url, options))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export function mp4File(name = 'clip.mp4', size = 1_000_000) {
  const file = new File(['ftyp'], name, { type: 'video/mp4' })
  Object.defineProperty(file, 'size', { value: size })
  return file
}
