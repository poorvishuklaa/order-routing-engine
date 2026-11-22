import {
  Box, Chip, Divider, FormControlLabel, IconButton, MenuItem, Stack, Switch, TextField, Tooltip, Typography,
} from '@mui/material'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import { label } from '../../utils/format.js'

const numberOrNull = (v) => (v === '' || v === null ? null : Number(v))

/** Where to send the order: hard constraints, ranking, and what to do on failure. */
export default function RuleDetails({ rule, meta, facilityGroups, onChange, readOnly }) {
  const set = (field) => (value) => onChange({ ...rule, [field]: value })
  const sorts = rule.facilitySorts || []
  const moveUp = (i) => {
    const next = [...sorts]
    ;[next[i - 1], next[i]] = [next[i], next[i - 1]]
    set('facilitySorts')(next)
  }

  return (
    <Stack spacing={1.5}>
      <Stack direction="row" spacing={1}>
        <TextField label="Rule name" value={rule.name} onChange={(e) => set('name')(e.target.value)} fullWidth disabled={readOnly} />
        <TextField select label="Status" value={rule.status} onChange={(e) => set('status')(e.target.value)} sx={{ width: 140 }} disabled={readOnly}>
          {meta.routingStatuses.map((s) => <MenuItem key={s} value={s}>{label(s)}</MenuItem>)}
        </TextField>
      </Stack>

      <Typography variant="overline" color="text.secondary">Facility filters</Typography>
      <TextField select label="Facility group (required)" value={rule.facilityGroupId ?? ''} disabled={readOnly}
        onChange={(e) => set('facilityGroupId')(e.target.value)} error={!rule.facilityGroupId}>
        {facilityGroups.map((g) => <MenuItem key={g.id} value={g.id}>{g.name} ({g.facilities.length})</MenuItem>)}
      </TextField>
      <Stack direction="row" spacing={1}>
        <TextField select label="Carrier" value={rule.carrier} onChange={(e) => set('carrier')(e.target.value)} fullWidth disabled={readOnly}>
          {meta.carriers.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
        </TextField>
        <TextField label="Safety stock" type="number" value={rule.safetyStock ?? 0} disabled={readOnly}
          onChange={(e) => set('safetyStock')(numberOrNull(e.target.value) ?? 0)} fullWidth />
      </Stack>
      <Stack direction="row" spacing={1}>
        <TextField label="Max distance (miles)" type="number" value={rule.maxDistanceMiles ?? ''} placeholder="No limit"
          onChange={(e) => set('maxDistanceMiles')(numberOrNull(e.target.value))} fullWidth disabled={readOnly} />
        <TextField label="Max transit days" type="number" value={rule.maxTransitDays ?? ''} placeholder="No limit"
          onChange={(e) => set('maxTransitDays')(numberOrNull(e.target.value))} fullWidth disabled={readOnly} />
      </Stack>
      <FormControlLabel disabled={readOnly} control={<Switch checked={rule.allowPartialAllocation} onChange={(e) => set('allowPartialAllocation')(e.target.checked)} />}
        label="Allow partial allocation (split order across facilities)" />
      <FormControlLabel disabled={readOnly} control={<Switch checked={rule.overrideCapacity} onChange={(e) => set('overrideCapacity')(e.target.checked)} />}
        label="Ignore facility daily capacity" />

      <Divider />
      <Typography variant="overline" color="text.secondary">Rank facilities by (in order)</Typography>
      <Box>
        {sorts.length === 0 && <Typography variant="body2" color="text.secondary">Default: transit time, then distance</Typography>}
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {sorts.map((s, i) => (
            <Chip key={s} label={`${i + 1}. ${label(s)}`} color="primary" variant="outlined"
              onDelete={readOnly ? undefined : () => set('facilitySorts')(sorts.filter((x) => x !== s))}
              icon={i > 0 && !readOnly ? (
                <Tooltip title="Move up"><IconButton size="small" sx={{ p: 0 }} onClick={() => moveUp(i)}><ArrowUpwardIcon sx={{ fontSize: 16 }} /></IconButton></Tooltip>
              ) : undefined} />
          ))}
        </Stack>
        {!readOnly && (
          <Stack direction="row" spacing={1} sx={{ mt: 1 }} flexWrap="wrap" useFlexGap>
            {meta.facilitySorts.filter((s) => !sorts.includes(s)).map((s) => (
              <Chip key={s} label={`+ ${label(s)}`} size="small" onClick={() => set('facilitySorts')([...sorts, s])} />
            ))}
          </Stack>
        )}
        <Typography variant="caption" color="text.secondary">Ties are broken by "covers most items", then facility id.</Typography>
      </Box>

      <Divider />
      <Typography variant="overline" color="text.secondary">If this rule cannot place items</Typography>
      <TextField select label="Action" value={rule.failAction} onChange={(e) => set('failAction')(e.target.value)} disabled={readOnly}>
        {meta.failActions.map((a) => <MenuItem key={a} value={a}>{label(a)}</MenuItem>)}
      </TextField>
      {rule.failAction === 'MOVE_TO_QUEUE' && (
        <TextField select label="Move to queue" value={rule.moveToQueueId ?? ''} onChange={(e) => set('moveToQueueId')(e.target.value)}
          error={!rule.moveToQueueId} disabled={readOnly}>
          {meta.queues.map((q) => <MenuItem key={q.id} value={q.id}>{q.code}</MenuItem>)}
        </TextField>
      )}
      {rule.failAction === 'AUTO_CANCEL' && (
        <TextField label="Auto-cancel after days" type="number" value={rule.autoCancelDays ?? ''} disabled={readOnly}
          onChange={(e) => set('autoCancelDays')(numberOrNull(e.target.value))} />
      )}
    </Stack>
  )
}
