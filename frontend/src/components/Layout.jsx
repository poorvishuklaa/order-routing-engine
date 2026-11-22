import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  AppBar, Box, Chip, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Tooltip, Typography,
} from '@mui/material'
import DashboardIcon from '@mui/icons-material/Dashboard'
import AltRouteIcon from '@mui/icons-material/AltRoute'
import FactCheckIcon from '@mui/icons-material/FactCheck'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import WarehouseIcon from '@mui/icons-material/Warehouse'
import LogoutIcon from '@mui/icons-material/Logout'
import { useAuth } from '../context/AuthContext.jsx'

const DRAWER_WIDTH = 230

const NAV = [
  { to: '/', label: 'Dashboard', icon: <DashboardIcon /> },
  { to: '/routing-groups', label: 'Routing Groups', icon: <AltRouteIcon /> },
  { to: '/decisions', label: 'Routing Decisions', icon: <FactCheckIcon /> },
  { to: '/orders', label: 'Orders', icon: <ReceiptLongIcon /> },
  { to: '/ai', label: 'AI Recommendations', icon: <AutoAwesomeIcon /> },
  { to: '/facilities', label: 'Facilities & Inventory', icon: <WarehouseIcon /> },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" elevation={0} sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <AltRouteIcon sx={{ mr: 1 }} />
          <Typography variant="h6" sx={{ flexGrow: 1 }}>Order Routing Engine</Typography>
          <Chip label={`${user.username} · ${user.role}`} size="small" sx={{ bgcolor: 'rgba(255,255,255,0.15)', color: 'white', mr: 1 }} />
          <Tooltip title="Log out">
            <IconButton color="inherit" onClick={() => { logout(); navigate('/login') }}><LogoutIcon /></IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>
      <Drawer variant="permanent" sx={{ width: DRAWER_WIDTH, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}>
        <Toolbar />
        <List>
          {NAV.map((item) => (
            <ListItemButton key={item.to} component={NavLink} to={item.to} end={item.to === '/'}
              sx={{ '&.active': { bgcolor: 'action.selected', borderRight: 3, borderColor: 'primary.main' } }}>
              <ListItemIcon sx={{ minWidth: 38 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 14 }} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3, minWidth: 0 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}
