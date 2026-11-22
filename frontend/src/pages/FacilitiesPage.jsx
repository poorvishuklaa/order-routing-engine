import { useEffect, useState } from 'react'
import {
  Alert, Autocomplete, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, Paper, Stack,
  Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs, TextField, Tooltip,
} from '@mui/material'
import EditIcon from '@mui/icons-material/Edit'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import AddIcon from '@mui/icons-material/Add'
import api, { errorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import PageHeader from '../components/PageHeader.jsx'

function CapacityDialog({ facility, onClose, onSaved }) {
  const [mode, setMode] = useState('number')
  const [value, setValue] = useState('')
  useEffect(() => {
    if (!facility) return
    if (facility.dailyCapacity == null) setMode('unlimited')
    else if (facility.dailyCapacity === 0) setMode('none')
    else { setMode('number'); setValue(facility.dailyCapacity) }
  }, [facility])
  const save = () => onSaved(mode === 'unlimited' ? null : mode === 'none' ? 0 : Number(value))
  return (
    <Dialog open={!!facility} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Daily capacity · {facility?.code}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField select label="Capacity" value={mode} onChange={(e) => setMode(e.target.value)}>
            <MenuItem value="unlimited">Unlimited</MenuItem>
            <MenuItem value="none">None (takes no orders)</MenuItem>
            <MenuItem value="number">Max orders per day</MenuItem>
          </TextField>
          {mode === 'number' && <TextField type="number" label="Orders per day" value={value} onChange={(e) => setValue(e.target.value)} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={mode === 'number' && !(Number(value) > 0)}>Save</Button>
      </DialogActions>
    </Dialog>
  )
}

function GroupDialog({ group, facilities, onClose, onSaved }) {
  const [form, setForm] = useState({ code: '', name: '', description: '', facilityIds: [] })
  useEffect(() => {
    if (group) setForm({ code: group.code ?? '', name: group.name ?? '', description: group.description ?? '', facilityIds: (group.facilities ?? []).map((f) => f.id) })
  }, [group])
  const shippable = facilities.filter((f) => f.type !== 'QUEUE')
  return (
    <Dialog open={!!group} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{group?.id ? 'Edit facility group' : 'New facility group'}</DialogTitle>
      <DialogContent>
        {group?.usedByRules?.length > 0 && (
          <Alert severity="warning" sx={{ mt: 1 }}>Used by {group.usedByRules.length} rule(s): changes affect routing right away.<br />{group.usedByRules.join(' · ')}</Alert>
        )}
        <Stack spacing={2} sx={{ mt: 2 }}>
          <TextField label="Code (max 20)" value={form.code} inputProps={{ maxLength: 20 }} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} />
          <TextField label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <TextField label="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <Autocomplete multiple options={shippable} getOptionLabel={(f) => `${f.code} — ${f.name}`}
            value={shippable.filter((f) => form.facilityIds.includes(f.id))}
            onChange={(e, v) => setForm({ ...form, facilityIds: v.map((f) => f.id) })}
            renderInput={(params) => <TextField {...params} label="Facilities" />} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!form.code || !form.name} onClick={() => onSaved(form)}>Save</Button>
      </DialogActions>
    </Dialog>
  )
}

export default function FacilitiesPage() {
  const { isAdmin } = useAuth()
  const [tab, setTab] = useState(0)
  const [facilities, setFacilities] = useState([])
  const [groups, setGroups] = useState([])
  const [inventory, setInventory] = useState([])
  const [facilityFilter, setFacilityFilter] = useState('')
  const [capacityTarget, setCapacityTarget] = useState(null)
  const [groupTarget, setGroupTarget] = useState(null)
  const [error, setError] = useState('')

  const load = () => Promise.all([api.get('/facilities'), api.get('/facility-groups'), api.get('/inventory')])
    .then(([f, g, i]) => { setFacilities(f.data); setGroups(g.data); setInventory(i.data) })
    .catch((e) => setError(errorMessage(e)))
  useEffect(() => { load() }, [])

  const run = async (fn) => {
    try { setError(''); await fn(); load() } catch (e) { setError(errorMessage(e)) }
  }

  return (
    <>
      <PageHeader title="Facilities & Inventory" />
      {error && <Alert severity="error" onClose={() => setError('')} sx={{ mb: 2 }}>{error}</Alert>}
      <Tabs value={tab} onChange={(e, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Facilities" /><Tab label="Facility groups" /><Tab label="Inventory" />
      </Tabs>

      {tab === 0 && (
        <Paper>
          <Table size="small">
            <TableHead><TableRow><TableCell>Code</TableCell><TableCell>Name</TableCell><TableCell>Type</TableCell><TableCell>Postal code</TableCell><TableCell align="right">Orders today</TableCell><TableCell align="right">Daily capacity</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {facilities.map((f) => (
                <TableRow key={f.id}>
                  <TableCell>{f.code}</TableCell><TableCell>{f.name}</TableCell><TableCell><Chip size="small" label={f.type} /></TableCell>
                  <TableCell>{f.postalCode ?? '—'}</TableCell><TableCell align="right">{f.type === 'QUEUE' ? '—' : f.ordersToday}</TableCell>
                  <TableCell align="right">{f.type === 'QUEUE' ? '—' : f.dailyCapacity == null ? 'Unlimited' : f.dailyCapacity}</TableCell>
                  <TableCell align="right">
                    {isAdmin && f.type !== 'QUEUE' && <Tooltip title="Edit capacity"><IconButton size="small" onClick={() => setCapacityTarget(f)}><EditIcon fontSize="small" /></IconButton></Tooltip>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}

      {tab === 1 && (
        <>
          {isAdmin && <Button startIcon={<AddIcon />} variant="contained" sx={{ mb: 2 }} onClick={() => setGroupTarget({})}>New facility group</Button>}
          <Paper>
            <Table size="small">
              <TableHead><TableRow><TableCell>Code</TableCell><TableCell>Name</TableCell><TableCell>Facilities</TableCell><TableCell>Used by rules</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {groups.map((g) => (
                  <TableRow key={g.id}>
                    <TableCell>{g.code}</TableCell><TableCell>{g.name}</TableCell>
                    <TableCell><Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>{g.facilities.map((f) => <Chip key={f.id} size="small" label={f.code} />)}</Stack></TableCell>
                    <TableCell sx={{ maxWidth: 320 }}>{g.usedByRules.length ? g.usedByRules.join(' · ') : '—'}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {isAdmin && <IconButton size="small" onClick={() => setGroupTarget(g)}><EditIcon fontSize="small" /></IconButton>}
                      {isAdmin && (
                        <Tooltip title={g.usedByRules.length ? 'In use by rules' : 'Delete'}>
                          <span><IconButton size="small" disabled={g.usedByRules.length > 0} onClick={() => run(() => api.delete(`/facility-groups/${g.id}`))}><DeleteOutlineIcon fontSize="small" /></IconButton></span>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}

      {tab === 2 && (
        <>
          <TextField select label="Facility" value={facilityFilter} onChange={(e) => setFacilityFilter(e.target.value)} sx={{ mb: 2, width: 260 }}>
            <MenuItem value="">All facilities</MenuItem>
            {facilities.filter((f) => f.type !== 'QUEUE').map((f) => <MenuItem key={f.id} value={f.code}>{f.code}</MenuItem>)}
          </TextField>
          <Paper>
            <Table size="small">
              <TableHead><TableRow><TableCell>Facility</TableCell><TableCell>SKU</TableCell><TableCell>Product</TableCell><TableCell align="right">On hand</TableCell><TableCell align="right">Reserved</TableCell><TableCell align="right">Min stock</TableCell><TableCell align="right">Available</TableCell><TableCell>Brokering</TableCell></TableRow></TableHead>
              <TableBody>
                {inventory.filter((i) => !facilityFilter || i.facilityCode === facilityFilter).map((i) => (
                  <TableRow key={i.id}>
                    <TableCell>{i.facilityCode}</TableCell><TableCell>{i.sku}</TableCell><TableCell>{i.productName}</TableCell>
                    <TableCell align="right">{i.onHand}</TableCell><TableCell align="right">{i.reserved}</TableCell><TableCell align="right">{i.minStock}</TableCell>
                    <TableCell align="right"><b>{i.available}</b></TableCell><TableCell>{i.allowBrokering ? 'Allowed' : 'Off'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}

      <CapacityDialog facility={capacityTarget} onClose={() => setCapacityTarget(null)}
        onSaved={(cap) => { const f = capacityTarget; setCapacityTarget(null); run(() => api.put(`/facilities/${f.id}/capacity`, { dailyCapacity: cap })) }} />
      <GroupDialog group={groupTarget} facilities={facilities} onClose={() => setGroupTarget(null)}
        onSaved={(form) => { const g = groupTarget; setGroupTarget(null); run(() => (g.id ? api.put(`/facility-groups/${g.id}`, form) : api.post('/facility-groups', form))) }} />
    </>
  )
}
