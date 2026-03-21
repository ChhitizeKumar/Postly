import { useEffect, useCallback, useState } from 'react'
import { io, Socket } from 'socket.io-client'
import { useAuthStore } from '@/store/authStore'
import toast from 'react-hot-toast'

interface Notification {
  id: string
  type: 'POST_LIKED' | 'NEW_FOLLOWER' | 'COMMENT' | 'AI_JOB_COMPLETE' | 'MENTION'
  title: string
  message: string
  link?: string
  read: boolean
  createdAt: string
}

interface UseNotificationsReturn {
  notifications: Notification[]
  unreadCount: number
  markAsRead: (id: string) => void
  markAllAsRead: () => void
  connected: boolean
}

let socket: Socket | null = null

export const useNotifications = (): UseNotificationsReturn => {
  const { accessToken, isAuthenticated, user } = useAuthStore()
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!isAuthenticated || !accessToken) return

    // Connect to notification service via WebSocket
    socket = io('/ws/notifications', {
      auth: { token: accessToken },
      transports: ['websocket'],
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionAttempts: 5,
    })

    socket.on('connect', () => {
      console.log('[WS] Connected to notification service')
      setConnected(true)
    })

    socket.on('disconnect', () => {
      console.log('[WS] Disconnected')
      setConnected(false)
    })

    // Incoming notification from server
    socket.on('notification', (notification: Notification) => {
      setNotifications((prev) => [notification, ...prev])

      // Show toast based on type
      const icon = {
        POST_LIKED: '❤️',
        NEW_FOLLOWER: '👤',
        COMMENT: '💬',
        AI_JOB_COMPLETE: '🤖',
        MENTION: '@',
      }[notification.type] ?? '🔔'

      toast(notification.message, {
        icon,
        duration: 4000,
        position: 'bottom-right',
      })
    })

    // Load initial unread notifications
    socket.on('initial_notifications', (initial: Notification[]) => {
      setNotifications(initial)
    })

    return () => {
      socket?.disconnect()
      socket = null
    }
  }, [isAuthenticated, accessToken])

  const markAsRead = useCallback((id: string) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    )
    socket?.emit('mark_read', { notificationId: id })
  }, [])

  const markAllAsRead = useCallback(() => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
    socket?.emit('mark_all_read', { userId: user?.id })
  }, [user?.id])

  const unreadCount = notifications.filter((n) => !n.read).length

  return { notifications, unreadCount, markAsRead, markAllAsRead, connected }
}
