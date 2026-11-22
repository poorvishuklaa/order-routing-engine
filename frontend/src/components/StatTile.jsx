import { Paper, Typography } from '@mui/material'

/** A headline number. Used instead of a chart when the job is a single value. */
export default function StatTile({ label, value, hint }) {
  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight={600} sx={{ my: 0.5 }}>{value}</Typography>
      {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
    </Paper>
  )
}
