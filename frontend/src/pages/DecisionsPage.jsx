import { useEffect, useState } from 'react'
import {
  Alert, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TablePagination, TableRow, TextField,
} from '@mui/material'
import api, { errorMessage } from '../api/client.js'
import PageHeader from '../components/PageHeader.jsx'
import StatusChip from '../components/StatusChip.jsx'
import { formatDateTime } from '../utils/format.js'

const OUTCOMES = ['ALLOCATED', 'NOT_ROUTED', 'MOVED_TO_QUEUE', 'REJECTED']

/** Audit trail: which rule sent each item where, and why. */
export default function DecisionsPage() {
  const [orderNumber, setOrderNumber] = useState('')
  const [outcome, setOutcome] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(25)
  const [data, setData] = useState({ content: [], totalElements: 0 })
  const [error, setError] = useState('')

  useEffect(() => {
    const t = setTimeout(() => {
      api.get('/routing-decisions', { params: { orderNumber: orderNumber || undefined, outcome: outcome || undefined, page, size } })
        .then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
    }, 250)
    return () => clearTimeout(t)
  }, [orderNumber, outcome, page, size])

  return (
    <>
      <PageHeader title="Routing Decisions" subtitle="Every attempt by every rule, with the reason" />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField label="Order number" value={orderNumber} onChange={(e) => { setOrderNumber(e.target.value); setPage(0) }} />
        <TextField select label="Outcome" value={outcome} onChange={(e) => { setOutcome(e.target.value); setPage(0) }} sx={{ width: 200 }}>
          <MenuItem value="">All</MenuItem>
          {OUTCOMES.map((o) => <MenuItem key={o} value={o}>{o.replaceAll('_', ' ')}</MenuItem>)}
        </TextField>
      </Stack>
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Time</TableCell><TableCell>Order</TableCell><TableCell>Item</TableCell><TableCell>Routing / Rule</TableCell>
              <TableCell>Outcome</TableCell><TableCell>Facility</TableCell><TableCell align="right">Transit</TableCell>
              <TableCell align="right">Miles</TableCell><TableCell>Reason</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.content.map((d) => (
              <TableRow key={d.id} hover>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(d.createdAt)}</TableCell>
                <TableCell>{d.orderNumber}</TableCell>
                <TableCell>{d.productSku} × {d.quantity}</TableCell>
                <TableCell>{d.routingName ? `${d.routingName} / ${d.ruleName}` : '—'}</TableCell>
                <TableCell><StatusChip status={d.outcome} /></TableCell>
                <TableCell>{d.facilityCode ?? '—'}</TableCell>
                <TableCell align="right">{d.transitDays != null ? `${d.transitDays}d` : '—'}</TableCell>
                <TableCell align="right">{d.distanceMiles != null ? Math.round(d.distanceMiles) : '—'}</TableCell>
                <TableCell sx={{ maxWidth: 380 }}>{d.reason}</TableCell>
              </TableRow>
            ))}
            {!data.content.length && <TableRow><TableCell colSpan={9}>No decisions yet. Run a routing group.</TableCell></TableRow>}
          </TableBody>
        </Table>
        <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={size}
          onPageChange={(e, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
          rowsPerPageOptions={[25, 50, 100]} />
      </Paper>
    </>
  )
}
