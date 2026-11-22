import { useState } from 'react'
import {
  Alert, Autocomplete, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import api, { errorMessage } from '../../api/client.js'
import StatusChip from '../StatusChip.jsx'

/** Dry run on one real order. The backend rolls back, so nothing changes. */
export default function TestDriveDialog({ groupId, open, onClose }) {
  const [options, setOptions] = useState([])
  const [order, setOrder] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const search = async (query) => {
    const { data } = await api.get('/orders', { params: { query, size: 20 } })
    setOptions(data.content)
  }

  const run = async () => {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.post(`/routing-groups/${groupId}/test-drive`, { orderId: order.id })
      setResult(data)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle>Test drive</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>Dry run: the saved group is run on one order and every change is rolled back. No stock is reserved.</Alert>
        <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
          <Autocomplete sx={{ flexGrow: 1 }} options={options} value={order} onOpen={() => search('')}
            onInputChange={(e, v, reason) => reason === 'input' && search(v)} onChange={(e, v) => { setOrder(v); setResult(null) }}
            getOptionLabel={(o) => `${o.orderNumber} · ${o.customerName} · ${o.shipToPostalCode} · ${o.shippingMethod} · ${o.allocatedCount}/${o.itemCount} allocated`}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => <TextField {...params} label="Order (search by number or customer)" />} />
          <Button variant="contained" disabled={!order || loading} onClick={run}>{loading ? 'Running…' : 'Run test'}</Button>
        </Stack>
        {error && <Alert severity="error">{error}</Alert>}
        {result && (
          <Stack spacing={2}>
            <Alert severity={result.matchedAnyRouting ? 'success' : 'warning'}>{result.message}</Alert>
            <Typography variant="subtitle2">Steps</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>Item</TableCell><TableCell>Routing / Rule</TableCell><TableCell>Outcome</TableCell><TableCell>Facility</TableCell><TableCell>Reason</TableCell></TableRow></TableHead>
              <TableBody>
                {result.steps.map((s, i) => (
                  <TableRow key={i}>
                    <TableCell>{s.productSku} × {s.quantity}</TableCell><TableCell>{s.routingName} / {s.ruleName}</TableCell>
                    <TableCell><StatusChip status={s.outcome} /></TableCell><TableCell>{s.facilityCode ?? '—'}</TableCell><TableCell>{s.reason}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Typography variant="subtitle2">Stock before the test (available = on hand − reserved − min stock)</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>Facility</TableCell><TableCell>SKU</TableCell><TableCell align="right">On hand</TableCell><TableCell align="right">Reserved</TableCell><TableCell align="right">Available</TableCell><TableCell>Brokering</TableCell></TableRow></TableHead>
              <TableBody>
                {result.stockBefore.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell>{s.facilityCode}</TableCell><TableCell>{s.sku}</TableCell><TableCell align="right">{s.onHand}</TableCell>
                    <TableCell align="right">{s.reserved}</TableCell><TableCell align="right">{s.available}</TableCell><TableCell>{s.allowBrokering ? 'Yes' : 'No'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        )}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Close</Button></DialogActions>
    </Dialog>
  )
}
