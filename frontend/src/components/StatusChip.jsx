import { Chip } from '@mui/material'

const COLORS = {
  ACTIVE: 'success', DRAFT: 'default', ARCHIVED: 'warning',
  COMPLETED: 'success', RUNNING: 'info', FAILED: 'error', SKIPPED: 'warning',
  ALLOCATED: 'success', NOT_ROUTED: 'warning', MOVED_TO_QUEUE: 'info', REJECTED: 'error',
  QUEUED: 'default', CANCELLED: 'error',
  PENDING: 'info', APPROVED: 'success', NO_CHANGES: 'default',
}

export default function StatusChip({ status, size = 'small' }) {
  return <Chip label={status?.replaceAll('_', ' ')} color={COLORS[status] || 'default'} size={size} variant="outlined" />
}
