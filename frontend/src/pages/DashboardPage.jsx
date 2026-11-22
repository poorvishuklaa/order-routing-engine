import { useEffect, useState } from 'react'
import {
  Alert, MenuItem, Paper, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import Grid from '@mui/material/Grid2'
import api, { errorMessage } from '../api/client.js'
import PageHeader from '../components/PageHeader.jsx'
import StatTile from '../components/StatTile.jsx'
import SimpleBarChart from '../components/SimpleBarChart.jsx'
import StatusChip from '../components/StatusChip.jsx'
import { formatDateTime } from '../utils/format.js'

function Section({ title, children }) {
  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="subtitle1" fontWeight={600} gutterBottom>{title}</Typography>
      {children}
    </Paper>
  )
}

export default function DashboardPage() {
  const [days, setDays] = useState(7)
  const [data, setData] = useState(null)
  const [runs, setRuns] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([api.get('/dashboard', { params: { days } }), api.get('/routing-runs', { params: { size: 8 } })])
      .then(([dash, runPage]) => { setData(dash.data); setRuns(runPage.data.content); setError('') })
      .catch((e) => setError(errorMessage(e)))
  }, [days])

  const s = data?.stats
  return (
    <>
      <PageHeader title="Dashboard" subtitle="How routing performed"
        actions={
          <TextField select label="Period" value={days} onChange={(e) => setDays(e.target.value)} sx={{ width: 150 }}>
            {[1, 7, 30].map((d) => <MenuItem key={d} value={d}>Last {d} day{d > 1 ? 's' : ''}</MenuItem>)}
          </TextField>
        } />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {s && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 6, md: 3 }}><StatTile label="Items attempted" value={s.itemsAttempted} /></Grid>
          <Grid size={{ xs: 6, md: 3 }}>
            <StatTile label="Allocation rate" value={`${s.allocationRatePercent}%`} hint={`${s.itemsAllocated} items allocated`} />
          </Grid>
          <Grid size={{ xs: 6, md: 3 }}>
            <StatTile label="Avg transit days" value={s.averageTransitDays ?? '—'} hint="Ground transit, allocated items" />
          </Grid>
          <Grid size={{ xs: 6, md: 3 }}>
            <StatTile label="Shipped from stores" value={s.storeAllocations}
              hint={`${s.warehouseAllocations} from warehouses · ${s.itemsNotRouted + s.itemsMovedToQueue} not routed`} />
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Section title="Allocations by facility">
              {s.allocationsByFacility.length
                ? <SimpleBarChart data={s.allocationsByFacility} xKey="key" yKey="count" valueLabel="Items allocated" />
                : <Typography color="text.secondary">No allocations yet. Run a routing group.</Typography>}
            </Section>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Section title="Average transit days by customer region">
              {s.transitByRegion.length
                ? <SimpleBarChart data={s.transitByRegion.map((r) => ({ ...r, region: r.area.split(',')[0] }))}
                    xKey="region" yKey="averageTransitDays" valueLabel="Avg transit days" horizontal />
                : <Typography color="text.secondary">No data.</Typography>}
            </Section>
          </Grid>

          <Grid size={{ xs: 12, md: 7 }}>
            <Section title="Why items were not routed">
              <Table size="small">
                <TableHead><TableRow><TableCell>Rule</TableCell><TableCell>Reason</TableCell><TableCell align="right">Items</TableCell></TableRow></TableHead>
                <TableBody>
                  {data.unroutedReasons.map((r) => (
                    <TableRow key={r.rule + r.reason}><TableCell>{r.rule}</TableCell><TableCell>{r.reason}</TableCell><TableCell align="right">{r.items}</TableCell></TableRow>
                  ))}
                  {!data.unroutedReasons.length && <TableRow><TableCell colSpan={3}>Nothing unrouted 🎉</TableCell></TableRow>}
                </TableBody>
              </Table>
            </Section>
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <Section title="Facility capacity today">
              <Table size="small">
                <TableHead><TableRow><TableCell>Facility</TableCell><TableCell align="right">Orders</TableCell><TableCell align="right">Capacity</TableCell><TableCell align="right">Used</TableCell></TableRow></TableHead>
                <TableBody>
                  {data.capacity.map((c) => (
                    <TableRow key={c.facilityCode}>
                      <TableCell>{c.facilityCode}</TableCell><TableCell align="right">{c.ordersToday}</TableCell>
                      <TableCell align="right">{c.dailyCapacity ?? '∞'}</TableCell><TableCell align="right">{c.utilization}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Section>
          </Grid>

          <Grid size={12}>
            <Section title="Recent routing runs">
              <Table size="small">
                <TableHead><TableRow><TableCell>Run</TableCell><TableCell>Group</TableCell><TableCell>Trigger</TableCell><TableCell>Status</TableCell><TableCell>Started</TableCell><TableCell align="right">Tried</TableCell><TableCell align="right">Placed</TableCell><TableCell>Message</TableCell></TableRow></TableHead>
                <TableBody>
                  {runs.map((r) => (
                    <TableRow key={r.id}>
                      <TableCell>#{r.id}</TableCell><TableCell>{r.routingGroupName ?? '—'}</TableCell><TableCell>{r.triggerType}</TableCell>
                      <TableCell><StatusChip status={r.status} /></TableCell><TableCell>{formatDateTime(r.startedAt)}</TableCell>
                      <TableCell align="right">{r.itemsTried}</TableCell><TableCell align="right">{r.itemsPlaced}</TableCell>
                      <TableCell sx={{ maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.errorMessage}</TableCell>
                    </TableRow>
                  ))}
                  {!runs.length && <TableRow><TableCell colSpan={8}>No runs yet</TableCell></TableRow>}
                </TableBody>
              </Table>
            </Section>
          </Grid>
        </Grid>
      )}
    </>
  )
}
