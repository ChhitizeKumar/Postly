import { Suspense, lazy } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { Toaster } from 'react-hot-toast'
import { useAuthStore } from '@/store/authStore'

// Lazy loaded pages - code splitting for performance
const HomePage        = lazy(() => import('@/pages/HomePage'))
const LoginPage       = lazy(() => import('@/pages/auth/LoginPage'))
const RegisterPage    = lazy(() => import('@/pages/auth/RegisterPage'))
const PostPage        = lazy(() => import('@/pages/PostPage'))
const EditorPage      = lazy(() => import('@/pages/EditorPage'))
const ProfilePage     = lazy(() => import('@/pages/ProfilePage'))
const FeedPage        = lazy(() => import('@/pages/FeedPage'))
const DashboardPage   = lazy(() => import('@/pages/DashboardPage'))
const AnalyticsPage   = lazy(() => import('@/pages/AnalyticsPage'))
const NotFoundPage    = lazy(() => import('@/pages/NotFoundPage'))

// Query client with smart defaults
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 2,        // 2 minutes
      gcTime: 1000 * 60 * 10,          // 10 minutes cache
      retry: 2,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 1,
    },
  },
})

// Protected route wrapper
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <>{children}</>
}

// Page loader fallback
function PageLoader() {
  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="flex flex-col items-center gap-4">
        <div className="w-8 h-8 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin" />
        <p className="text-sm text-gray-500">Loading...</p>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<PageLoader />}>
          <Routes>
            {/* Public routes */}
            <Route path="/"             element={<HomePage />} />
            <Route path="/login"        element={<LoginPage />} />
            <Route path="/register"     element={<RegisterPage />} />
            <Route path="/post/:slug"   element={<PostPage />} />
            <Route path="/u/:username"  element={<ProfilePage />} />
            <Route path="/tag/:tag"     element={<FeedPage />} />

            {/* Protected routes */}
            <Route path="/feed" element={
              <ProtectedRoute><FeedPage /></ProtectedRoute>
            } />
            <Route path="/write" element={
              <ProtectedRoute><EditorPage /></ProtectedRoute>
            } />
            <Route path="/write/:id" element={
              <ProtectedRoute><EditorPage /></ProtectedRoute>
            } />
            <Route path="/dashboard" element={
              <ProtectedRoute><DashboardPage /></ProtectedRoute>
            } />
            <Route path="/analytics" element={
              <ProtectedRoute><AnalyticsPage /></ProtectedRoute>
            } />

            {/* 404 */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
      </BrowserRouter>

      {/* Global toast notifications */}
      <Toaster
        position="bottom-right"
        toastOptions={{
          duration: 4000,
          style: {
            background: '#1f2937',
            color: '#f9fafb',
            borderRadius: '10px',
          },
        }}
      />

      {/* React Query devtools - only in dev */}
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  )
}
