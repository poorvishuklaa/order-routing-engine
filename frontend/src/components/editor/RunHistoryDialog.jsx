import { useEffect, useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material'
import api from '../../api/client.js'
import StatusChip from '../StatusChip.jsx'
import { formatDateTime } from '../../utils/format.js'

export default function RunHistoryDialog({ groupId, open, onClose }) {
  const [runs, setRuns] = useState([])
  useEffect(() => {
    if (open) api.get('/routing-runs', { params: { groupId, size: 50 } }).then((r) => setRuns(r.data.content))
  }, [open, groupId])

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Run history</DialogTitle>
      <DialogContent>
        <Table size="small">
          <TableHead><TableRow><TableCell>Run</TableCell><TableCell>Trigger</TableCell><TableCell>Status</TableCell><TableCell>Started</TableCell><TableCell>Finished</TableCell><TableCell align="right">Tried</TableCell><TableCell align="right">Placed</TableCell><TableCell>Error</TableCell></TableRow></TableHead>
          <TableBody>
            {runs.map((r) => (
              <TableRow key={r.id}>
                <TableCell>#{r.id}</TableCell><TableCell>{r.triggerType}</TableCell><TableCell><StatusChip status={r.status} /></TableCell>
                <TableCell>{formatDateTime(r.startedAt)}</TableCell><TableCell>{formatDateTime(r.finishedAt)}</TableCell>
                <TableCell align="right">{r.itemsTried}</TableCell><TableCell align="right">{r.itemsPlaced}</TableCell><TableCell>{r.errorMessage}</TableCell>
              </TableRow>
            ))}
            {!runs.length && <TableRow><TableCell colSpan={8}>No runs yet</TableCell></TableRow>}
          </TableBody>
        </Table>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Close</Button></DialogActions>
    </Dialog>
  )
}
