/**
 * SSE streaming client using native fetch() + ReadableStream.
 *
 * Uses fetch instead of EventSource because:
 * 1. EventSource doesn't support POST requests.
 * 2. AbortController.abort() properly severs the HTTP connection,
 *    which triggers the Spring WebFlux backend's .doOnCancel().
 *
 * @param {Object}   payload    - { modelId, message, sessionId }
 * @param {string}   token      - JWT Bearer token
 * @param {Function} onChunk    - called for each parsed chunk { content, done }
 * @param {Function} onDone     - called with full accumulated content when stream ends
 * @param {AbortSignal} signal  - AbortController signal for stop-generation
 */
export async function streamChat(payload, token, onChunk, onDone, signal) {
  const response = await fetch('/api/v1/chat/stream', {
    method:  'POST',
    headers: {
      'Content-Type':  'application/json',
      'Authorization': `Bearer ${token}`,
      'Accept':        'text/event-stream',
      'Cache-Control': 'no-cache'
    },
    body:   JSON.stringify(payload),
    signal
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`HTTP ${response.status}: ${text}`)
  }

  const reader     = response.body.getReader()
  const decoder    = new TextDecoder('utf-8')
  let   buffer     = ''
  let   fullContent= ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // SSE lines are separated by '\n\n'
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''  // last incomplete line stays in buffer

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || trimmed.startsWith(':')) continue   // SSE comment / heartbeat

        if (trimmed.startsWith('data:')) {
          const dataStr = trimmed.slice(5).trim()
          if (dataStr === '[DONE]') {
            onDone(fullContent)
            return
          }

          try {
            const chunk = JSON.parse(dataStr)
            if (chunk.content) fullContent += chunk.content
            onChunk(chunk)
            if (chunk.done) {
              onDone(fullContent)
              return
            }
          } catch {
            // Non-JSON line — ignore
          }
        }
      }
    }
  } finally {
    reader.cancel()
  }

  // Stream ended without explicit [DONE]
  onDone(fullContent)
}
