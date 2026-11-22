import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper, Snackbar, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, ToggleButton, ToggleButtonGroup, Tooltip,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import PauseIcon from '@mui/icons-material/Pause'
import BoltIcon from '@mui/icons-material/Bolt'
import EditIcon from '@mui/icons-material/Edit'
import api, { errorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import PageHeader from '../components/PageHeader.jsx'
import StatusChip from '../components/StatusChip.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import { formatDateTime } from '../utils/format.js'

export default function RoutingGroupsPage() {
  const { isAdmin } = useAuth()
  const navigate = useNavigate()
  const [groups, setGroups] = useState([])
  const [filter, setFilter] = useState('ALL')
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [newName, setNewName] = useState('')
  const [runTarget, setRunTarget] = useState(null)

  const load = () => api.get('/routing-groups').then((r) => setGroups(r.data)).catch((e) => setError(errorMessage(e)))
  useEffect(() => { load() }, [])

  const act = async (fn, message) => {
    try {
      await fn()
      setToast(message)
      load()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const create = async () => {
    try {
      const { data } = await api.post('/routing-groups', { name: newName })
      navigate(`/routing-groups/${data.id}`)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const runNow = async () => {
    const group = runTarget
    setRunTarget(null)
    await act(async () => {
      const { data } = await api.post(`/routing-groups/${group.id}/run`)
      setToast(`Run #${data.id} ${data.status}: ${data.itemsPlaced} of ${data.itemsTried} items placed`)
    }, '')
  }

  const shown = groups
    .filter((g) => filter === 'ALL' || g.status === filter)
    .filter((g) => g.name.toLowerCase().includes(search.toLowerCase()))

  return (
    <>
      <PageHeader title="Routing Groups" subtitle="Each group runs on its own schedule and routes waiting orders"
        actions={isAdmin && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>New group</Button>} />
      {error && <Alert severity="error" onClose={() => setError('')} sx={{ mb: 2 }}>{error}</Alert>}
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <ToggleButtonGroup size="small" exclusive value={filter} onChange={(e, v) => v && setFilter(v)}>
          <ToggleButton value="ALL">All</ToggleButton>
          <ToggleButton value="ACTIVE">Active</ToggleButton>
          <ToggleButton value="DRAFT">Draft</ToggleButton>
        </ToggleButtonGroup>
        <TextField placeholder="Search by name" value={search} onChange={(e) => setSearch(e.target.value)} />
      </Stack>
      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell><TableCell>Status</TableCell><TableCell>Schedule</TableCell>
              <TableCell>Next run</TableCell><TableCell>Last run</TableCell><TableCell align="right">Routings</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {shown.map((g) => (
              <TableRow key={g.id} hover>
                <TableCell sx={{ fontWeight: 500, cursor: 'pointer' }} onClick={() => navigate(`/routing-groups/${g.id}`)}>{g.name}</TableCell>
                <TableCell><StatusChip status={g.status} /></TableCell>
                <TableCell>{g.scheduleDescription}</TableCell>
                <TableCell>{formatDateTime(g.nextRunAt)}</TableCell>
                <TableCell>{formatDateTime(g.lastRunAt)}</TableCell>
                <TableCell align="right">{g.routingCount}</TableCell>
                <TableCell align="right">
                  <Tooltip title="Open"><IconButton onClick={() => navigate(`/routing-groups/${g.id}`)}><EditIcon fontSize="small" /></IconButton></Tooltip>
                  {isAdmin && (g.status === 'ACTIVE'
                    ? <Tooltip title="Pause schedule (move to draft)"><IconButton onClick={() => act(() => api.post(`/routing-groups/${g.id}/pause`), 'Group paused')}><PauseIcon fontSize="small" /></IconButton></Tooltip>
                    : <Tooltip title="Activate schedule"><IconButton onClick={() => act(() => api.post(`/routing-groups/${g.id}/activate`), 'Group activated')}><PlayArrowIcon fontSize="small" /></IconButton></Tooltip>)}
                  {isAdmin && <Tooltip title="Run now"><IconButton color="warning" onClick={() => setRunTarget(g)}><BoltIcon fontSize="small" /></IconButton></Tooltip>}
                </TableCell>
              </TableRow>
            ))}
            {!shown.length && <TableRow><TableCell colSpan={7}>No routing groups</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Paper>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>New routing group</DialogTitle>
        <DialogContent>
          <TextField autoFocus fullWidth label="Name" value={newName} onChange={(e) => setNewName(e.target.value)} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={!newName.trim()} onClick={create}>Create</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog open={!!runTarget} title="Run now?" confirmText="Run now"
        message={`This routes REAL waiting orders with "${runTarget?.name}" right away and reserves stock. The schedule is not changed.`}
        onClose={() => setRunTarget(null)} onConfirm={runNow} />
      <Snackbar open={!!toast} autoHideDuration={5000} onClose={() => setToast('')} message={toast} />
    </>
  )
}
