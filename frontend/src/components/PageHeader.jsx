import { Box, Typography } from '@mui/material'

export default function PageHeader({ title, subtitle, actions }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
      <Box sx={{ flexGrow: 1 }}>
        <Typography variant="h5" fontWeight={600}>{title}</Typography>
        {subtitle && <Typography variant="body2" color="text.secondary">{subtitle}</Typography>}
      </Box>
      {actions}
    </Box>
  )
}
