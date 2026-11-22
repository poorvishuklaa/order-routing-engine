import { useEffect, useState } from 'react'
import {
  Alert, Box, Button, Card, CardActions, CardContent, Chip, LinearProgress, MenuItem, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import api, { errorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import PageHeader from '../components/PageHeader.jsx'
import StatusChip from '../components/StatusChip.jsx'
import { formatDateTime, label } from '../utils/format.js'

function RecommendationCard({ rec, isAdmin, onReview }) {
  const [note, setNote] = useState('')
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>{rec.title || 'Recommendation'}</Typography>
          <StatusChip status={rec.status} />
        </Stack>
        <Typography variant="caption" color="text.secondary">
          #{rec.id} · group {rec.routingGroupId} · {formatDateTime(rec.createdAt)} · by {rec.createdBy} · {rec.model}
        </Typography>
        {rec.reason && <Typography variant="body2" sx={{ mt: 1.5 }}><b>Why:</b> {rec.reason}</Typography>}
        {rec.expectedImpact && <Typography variant="body2" sx={{ mt: 0.5 }}><b>Expected impact:</b> {rec.expectedImpact}</Typography>}
        {rec.errorMessage && <Alert severity="error" sx={{ mt: 1.5 }}>{rec.errorMessage}</Alert>}

        {rec.changes.length > 0 && (
          <Table size="small" sx={{ mt: 1.5 }}>
            <TableHead><TableRow><TableCell>Routing / Rule</TableCell><TableCell>Field</TableCell><TableCell>Current</TableCell><TableCell /><TableCell>Suggested</TableCell></TableRow></TableHead>
            <TableBody>
              {rec.changes.map((c, i) => (
                <TableRow key={i}>
                  <TableCell>{c.routingName ? `${c.routingName} / ` : ''}{c.ruleName}</TableCell>
                  <TableCell>{label(c.field)}</TableCell>
                  <TableCell><code>{c.oldValue}</code></TableCell>
                  <TableCell>→</TableCell>
                  <TableCell><code><b>{c.newValue}</b></code></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {rec.toolCalls.length > 0 && (
          <Box sx={{ mt: 1.5 }}>
            <Typography variant="caption" color="text.secondary">Data the AI looked at: </Typography>
            {rec.toolCalls.map((t, i) => <Chip key={i} label={t} size="small" variant="outlined" sx={{ mr: 0.5, mb: 0.5 }} />)}
          </Box>
        )}
        {rec.reviewedBy && (
          <Typography variant="caption" display="block" sx={{ mt: 1 }}>
            {label(rec.status)} by {rec.reviewedBy} on {formatDateTime(rec.reviewedAt)}{rec.reviewNote ? ` — "${rec.reviewNote}"` : ''}
          </Typography>
        )}
      </CardContent>
      {isAdmin && rec.status === 'PENDING' && (
        <CardActions sx={{ px: 2, pb: 2, gap: 1 }}>
          <TextField placeholder="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} sx={{ flexGrow: 1 }} />
          <Button color="error" onClick={() => onReview(rec.id, 'reject', note)}>Reject</Button>
          <Button variant="contained" color="success" onClick={() => onReview(rec.id, 'approve', note)}>Approve & apply</Button>
        </CardActions>
      )}
    </Card>
  )
}

export default function AiRecommendationsPage() {
  const { isAdmin } = useAuth()
  const [status, setStatus] = useState(null)
  const [groups, setGroups] = useState([])
  const [groupId, setGroupId] = useState('')
  const [filter, setFilter] = useState('PENDING')
  const [recs, setRecs] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  const loadRecs = () => api.get('/ai/recommendations', { params: { status: filter === 'ALL' ? undefined : filter } })
    .then((r) => setRecs(r.data)).catch((e) => setError(errorMessage(e)))

  useEffect(() => {
    api.get('/ai/status').then((r) => setStatus(r.data))
    api.get('/routing-groups').then((r) => { setGroups(r.data); if (r.data.length) setGroupId(r.data[0].id) })
  }, [])
  useEffect(() => { loadRecs() }, [filter]) // eslint-disable-line react-hooks/exhaustive-deps

  const generate = async () => {
    setLoading(true)
    setError('')
    setInfo('')
    try {
      const { data } = await api.post('/ai/recommendations/generate', null, { params: { groupId } })
      setInfo(data.status === 'PENDING' ? 'New recommendation ready for review.'
        : data.status === 'NO_CHANGES' ? 'The AI found nothing worth changing.'
          : `The AI answer did not pass validation: ${data.errorMessage}`)
      setFilter(data.status === 'PENDING' ? 'PENDING' : 'ALL')
      loadRecs()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  const review = async (id, action, note) => {
    try {
      await api.post(`/ai/recommendations/${id}/${action}`, { note })
      setInfo(action === 'approve' ? 'Approved: the rule changes are live for the next routing run.' : 'Rejected. No rules were changed.')
      loadRecs()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <>
      <PageHeader title="AI Recommendations"
        subtitle="The AI reads routing results, stock and customer locations, and suggests rule changes. Nothing changes until an admin approves." />
      {status && !status.configured && (
        <Alert severity="warning" sx={{ mb: 2 }}>AI is not configured on the server. Set ANTHROPIC_API_KEY and restart the backend to generate recommendations.</Alert>
      )}
      {error && <Alert severity="error" onClose={() => setError('')} sx={{ mb: 2 }}>{error}</Alert>}
      {info && <Alert severity="info" onClose={() => setInfo('')} sx={{ mb: 2 }}>{info}</Alert>}

      <Stack direction="row" spacing={2} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        {isAdmin && (
          <>
            <TextField select label="Routing group" value={groupId} onChange={(e) => setGroupId(e.target.value)} sx={{ minWidth: 220 }}>
              {groups.map((g) => <MenuItem key={g.id} value={g.id}>{g.name}</MenuItem>)}
            </TextField>
            <Button variant="contained" startIcon={<AutoAwesomeIcon />} disabled={!groupId || loading || !status?.configured} onClick={generate}>
              {loading ? 'Analyzing…' : 'Get AI recommendations'}
            </Button>
          </>
        )}
        <Box sx={{ flexGrow: 1 }} />
        <ToggleButtonGroup size="small" exclusive value={filter} onChange={(e, v) => v && setFilter(v)}>
          {['PENDING', 'APPROVED', 'REJECTED', 'FAILED', 'ALL'].map((s) => <ToggleButton key={s} value={s}>{label(s)}</ToggleButton>)}
        </ToggleButtonGroup>
      </Stack>
      {loading && <LinearProgress sx={{ mb: 2 }} />}

      <Stack spacing={2}>
        {recs.map((r) => <RecommendationCard key={r.id} rec={r} isAdmin={isAdmin} onReview={review} />)}
        {!recs.length && <Typography color="text.secondary">No recommendations in this list.</Typography>}
      </Stack>
    </>
  )
}
