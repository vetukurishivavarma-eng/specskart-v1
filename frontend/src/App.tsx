import { Routes, Route, Navigate } from 'react-router-dom'
import SiteLayout from './components/SiteLayout'
import Home from './pages/Home'
import HowItWorks from './pages/HowItWorks'
import Contact from './pages/Contact'
import Store from './pages/Store'
import Privacy from './pages/Privacy'
import FrameFinder from './pages/FrameFinder'
import AdminLayout from './admin/AdminLayout'
import Login from './admin/Login'
import Dashboard from './admin/Dashboard'
import Leads from './admin/Leads'
import LeadDetail from './admin/LeadDetail'
import Campaigns from './admin/Campaigns'
import { auth } from './lib/auth'

function RequireAuth({ children }: { children: React.ReactNode }) {
  return auth.token ? <>{children}</> : <Navigate to="/admin/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route element={<SiteLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/how-it-works" element={<HowItWorks />} />
        <Route path="/contact" element={<Contact />} />
        <Route path="/store" element={<Store />} />
        <Route path="/privacy" element={<Privacy />} />
      </Route>
      <Route path="/frame-finder" element={<FrameFinder />} />

      <Route path="/admin/login" element={<Login />} />
      <Route path="/admin" element={<RequireAuth><AdminLayout /></RequireAuth>}>
        <Route index element={<Dashboard />} />
        <Route path="leads" element={<Leads />} />
        <Route path="leads/:id" element={<LeadDetail />} />
        <Route path="campaigns" element={<Campaigns />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
