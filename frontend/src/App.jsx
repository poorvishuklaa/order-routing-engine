import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import LoginPage from './pages/LoginPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import RoutingGroupsPage from './pages/RoutingGroupsPage.jsx'
import RoutingGroupEditorPage from './pages/RoutingGroupEditorPage.jsx'
import DecisionsPage from './pages/DecisionsPage.jsx'
import OrdersPage from './pages/OrdersPage.jsx'
import FacilitiesPage from './pages/FacilitiesPage.jsx'
import AiRecommendationsPage from './pages/AiRecommendationsPage.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/routing-groups" element={<RoutingGroupsPage />} />
        <Route path="/routing-groups/:id" element={<RoutingGroupEditorPage />} />
        <Route path="/decisions" element={<DecisionsPage />} />
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/facilities" element={<FacilitiesPage />} />
        <Route path="/ai" element={<AiRecommendationsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
