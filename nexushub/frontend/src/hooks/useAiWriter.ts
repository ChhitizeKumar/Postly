import { useState, useCallback, useRef } from 'react'
import { streamAiResponse } from '@/services/apiClient'

type WriteAction = 'IMPROVE' | 'EXPAND' | 'SHORTEN' | 'CONTINUE' | 'FIX_GRAMMAR'

interface UseAiWriterOptions {
  onComplete?: (fullText: string) => void
}

interface UseAiWriterReturn {
  streamedText: string
  isStreaming: boolean
  error: string | null
  stream: (action: WriteAction, text: string, context?: string, instruction?: string) => void
  cancel: () => void
  reset: () => void
}

/**
 * Hook for real-time AI writing assistance via SSE streaming.
 * Words appear one by one as Claude generates them.
 *
 * Usage:
 *   const { streamedText, isStreaming, stream } = useAiWriter({
 *     onComplete: (text) => editor.commands.setContent(text)
 *   })
 *
 *   stream('IMPROVE', selectedText)
 */
export const useAiWriter = ({ onComplete }: UseAiWriterOptions = {}): UseAiWriterReturn => {
  const [streamedText, setStreamedText] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const fullTextRef = useRef('')

  const stream = useCallback(
    (action: WriteAction, text: string, context?: string, instruction?: string) => {
      // Cancel any in-flight stream
      abortRef.current?.abort()
      abortRef.current = new AbortController()

      setStreamedText('')
      setError(null)
      setIsStreaming(true)
      fullTextRef.current = ''

      streamAiResponse(
        '/ai/write/stream',
        { action, text, context, instruction },
        (chunk) => {
          fullTextRef.current += chunk
          setStreamedText((prev) => prev + chunk)
        },
        () => {
          setIsStreaming(false)
          onComplete?.(fullTextRef.current)
        },
        (err) => {
          setIsStreaming(false)
          setError(err.message)
        }
      )
    },
    [onComplete]
  )

  const cancel = useCallback(() => {
    abortRef.current?.abort()
    setIsStreaming(false)
  }, [])

  const reset = useCallback(() => {
    cancel()
    setStreamedText('')
    setError(null)
    fullTextRef.current = ''
  }, [cancel])

  return { streamedText, isStreaming, error, stream, cancel, reset }
}
