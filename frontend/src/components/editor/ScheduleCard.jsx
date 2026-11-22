import { useEffect, useState } from 'react'
import { Alert, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import api, { errorMessage } from '../../api/client.js'
import StatusChip from '../StatusChip.jsx'

const PRESETS = [
  { cron: '0 0/5 * * * *', label: 'Every 5 minutes' },
  { cron: '0 0/15 * * * *', label: 'Every 15 minutes' },
  { cron: '0 0/30 * * * *', label: 'Every 30 minutes' },
  { cron: '0 0 * * * *', label: 'Every hour' },
  { cron: '0 0 0/6 * * *', label: 'Every 6 hours' },
  { cron: '0 0 0 * * *', label: 'Daily at midnight' },
]

/** Schedule is saved immediately on its own (not with the main Save). */
export default function ScheduleCard({ group, serverTimeZone, readOnly, onChanged }) {
  const isPreset = (c) => PRESETS.some((p) => p.cron === c)
  const [mode, setMode] = useState('0 0/15 * * * *')
  const [custom, setCustom] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!group.cronExpression || isPreset(group.cronExpression)) {
      setMode(group.cronExpression || '0 0/15 * * * *')
    } else {
      setMode('CUSTOM')
      setCustom(group.cronExpression)
    }
  }, [group.cronExpression])

  const call = async (fn) => {
    try {
      setError('')
      const { data } = await fn()
      onChanged(data)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const cron = mode === 'CUSTOM' ? custom : mode
  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ flexGrow: 1 }}>Schedule</Typography>
        <StatusChip status={group.status} />
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <TextField select label="Runs" value={mode} onChange={(e) => setMode(e.target.value)} sx={{ minWidth: 180 }} disabled={readOnly}>
          {PRESETS.map((p) => <MenuItem key={p.cron} value={p.cron}>{p.label}</MenuItem>)}
          <MenuItem value="CUSTOM">Custom cron…</MenuItem>
        </TextField>
        {mode === 'CUSTOM' && (
          <TextField label="Cron (sec min hour day month weekday)" value={custom} onChange={(e) => setCustom(e.target.value)}
            placeholder="0 0/10 * * * *" sx={{ minWidth: 260 }} disabled={readOnly} />
        )}
        {!readOnly && (
          <>
            <Button variant="outlined" disabled={!cron || cron === group.cronExpression}
              onClick={() => call(() => api.put(`/routing-groups/${group.id}/schedule`, { cronExpression: cron }))}>
              Save schedule
            </Button>
            {group.status === 'ACTIVE'
              ? <Button color="warning" onClick={() => call(() => api.post(`/routing-groups/${group.id}/pause`))}>Pause</Button>
              : <Button color="success" variant="contained" onClick={() => call(() => api.post(`/routing-groups/${group.id}/activate`))}>Activate</Button>}
          </>
        )}
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        Current: {group.cronExpression || 'not scheduled'} · Cron runs in the server timezone ({serverTimeZone}); times on screen are in your timezone.
      </Typography>
    </Paper>
  )
}
