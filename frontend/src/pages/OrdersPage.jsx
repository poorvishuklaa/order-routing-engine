import { useEffect, useState } from 'react'
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Drawer, Paper, Stack, Table, TableBody, TableCell,
  TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material'
import api, { errorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import PageHeader from '../components/PageHeader.jsx'
import StatusChip from '../components/StatusChip.jsx'
import { formatDateTime, label } from '../utils/format.js'

function OrderDrawer({ orderId, onClose }) {
  const { isAdmin } = useAuth()
  const [detail, setDetail] = useState(null)
  const [decisions, setDecisions] = useState([])
  const [rejectItem, setRejectItem] = useState(null)
  const [reason, setReason] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const load = () => {
    Promise.all([api.get(`/orders/${orderId}`), api.get(`/orders/${orderId}/decisions`)])
      .then(([o, d]) => { setDetail(o.data); setDecisions(d.data) })
      .catch((e) => setError(errorMessage(e)))
  }
  useEffect(() => { if (orderId) { setResult(null); load() } }, [orderId]) // eslint-disable-line react-hooks/exhaustive-deps

  const reject = async () => {
    try {
      const { data } = await api.post(`/order-items/${rejectItem.id}/reject`, { reason })
      setResult(data)
      setRejectItem(null)
      setReason('')
      load()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <Drawer anchor="right" open={!!orderId} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', md: 760 }, p: 3 } }}>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {detail && (
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6">{detail.order.orderNumber}</Typography>
            <Typography variant="body2" color="text.secondary">
              {detail.order.customerName} · ships to {detail.order.shipToPostalCode} · {label(detail.order.shippingMethod)} ·
              {' '}{label(detail.order.priority)} priority · {detail.order.salesChannel} · {formatDateTime(detail.order.orderDate)}
            </Typography>
          </Box>
          {result && <Alert severity={result.status === 'ALLOCATED' ? 'success' : 'warning'}>Rejected by {result.rejectedBy}. {result.message}</Alert>}
          <Typography variant="subtitle2">Items</Typography>
          <Table size="small">
            <TableHead><TableRow><TableCell>SKU</TableCell><TableCell align="right">Qty</TableCell><TableCell>Status</TableCell><TableCell>Facility</TableCell><TableCell>Auto-cancel</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {detail.items.map((i) => (
                <TableRow key={i.id}>
                  <TableCell>{i.sku}<Typography variant="caption" display="block" color="text.secondary">{i.productName}</Typography></TableCell>
                  <TableCell align="right">{i.quantity}</TableCell>
                  <TableCell><StatusChip status={i.status} /></TableCell>
                  <TableCell>{i.facilityCode}{i.facilityType === 'QUEUE' ? ' (queue)' : ''}</TableCell>
                  <TableCell>{i.autoCancelDate ?? '—'}</TableCell>
                  <TableCell align="right">
                    {isAdmin && i.status === 'ALLOCATED' && (
                      <Button size="small" color="error" onClick={() => setRejectItem(i)}>Facility rejects</Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Typography variant="subtitle2">Routing history</Typography>
          <Table size="small">
            <TableHead><TableRow><TableCell>Time</TableCell><TableCell>Item</TableCell><TableCell>Outcome</TableCell><TableCell>Facility</TableCell><TableCell>Reason</TableCell></TableRow></TableHead>
            <TableBody>
              {decisions.map((d) => (
                <TableRow key={d.id}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(d.createdAt)}</TableCell><TableCell>{d.productSku}</TableCell>
                  <TableCell><StatusChip status={d.outcome} /></TableCell><TableCell>{d.facilityCode ?? '—'}</TableCell><TableCell>{d.reason}</TableCell>
                </TableRow>
              ))}
              {!decisions.length && <TableRow><TableCell colSpan={5}>Not routed yet</TableCell></TableRow>}
            </TableBody>
          </Table>
        </Stack>
      )}
      <Dialog open={!!rejectItem} onClose={() => setRejectItem(null)} fullWidth maxWidth="xs">
        <DialogTitle>Facility cannot fulfill {rejectItem?.sku}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>
            The reservation at {rejectItem?.facilityCode} is released, that facility is excluded for this item, and the order is routed again right away.
          </Typography>
          <TextField fullWidth label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Damaged stock" />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectItem(null)}>Cancel</Button>
          <Button color="error" variant="contained" onClick={reject}>Reject and re-route</Button>
        </DialogActions>
      </Dialog>
    </Drawer>
  )
}

export default function OrdersPage() {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ content: [], totalElements: 0 })
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const t = setTimeout(() => {
      api.get('/orders', { params: { query: query || undefined, page, size: 20 } })
        .then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
    }, 250)
    return () => clearTimeout(t)
  }, [query, page, selected])

  return (
    <>
      <PageHeader title="Orders" subtitle="Approved orders and where their items were routed" />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <TextField label="Search order number or customer" value={query} onChange={(e) => { setQuery(e.target.value); setPage(0) }} sx={{ mb: 2, width: 340 }} />
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Order</TableCell><TableCell>Customer</TableCell><TableCell>Ship to</TableCell><TableCell>Shipping</TableCell>
              <TableCell>Priority</TableCell><TableCell>Channel</TableCell><TableCell>Ordered</TableCell><TableCell align="right">Allocated</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.content.map((o) => (
              <TableRow key={o.id} hover sx={{ cursor: 'pointer' }} onClick={() => setSelected(o.id)}>
                <TableCell>{o.orderNumber}</TableCell><TableCell>{o.customerName}</TableCell><TableCell>{o.shipToPostalCode}</TableCell>
                <TableCell>{label(o.shippingMethod)}</TableCell><TableCell>{label(o.priority)}</TableCell><TableCell>{o.salesChannel}</TableCell>
                <TableCell>{formatDateTime(o.orderDate)}</TableCell><TableCell align="right">{o.allocatedCount} / {o.itemCount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={20} rowsPerPageOptions={[20]}
          onPageChange={(e, p) => setPage(p)} />
      </Paper>
      <OrderDrawer orderId={selected} onClose={() => setSelected(null)} />
    </>
  )
}
