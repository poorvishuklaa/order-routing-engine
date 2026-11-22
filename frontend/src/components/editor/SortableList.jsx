import { IconButton, List, ListItemButton, ListItemText, Stack, Tooltip } from '@mui/material'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import StatusChip from '../StatusChip.jsx'

/** A list of routings or rules with select, reorder, clone and delete. */
export default function SortableList({ items, selected, onSelect, onMove, onClone, onDelete, secondary, readOnly }) {
  return (
    <List dense disablePadding>
      {items.map((item, i) => (
        <ListItemButton key={item._key} selected={i === selected} onClick={() => onSelect(i)}
          sx={{ borderRadius: 1, mb: 0.5, opacity: item.status === 'ARCHIVED' ? 0.5 : 1, alignItems: 'flex-start' }}>
          <ListItemText primary={`${i + 1}. ${item.name || '(unnamed)'}`} secondary={secondary?.(item)}
            primaryTypographyProps={{ fontWeight: 500 }} />
          <Stack alignItems="flex-end" spacing={0.5}>
            <StatusChip status={item.status} />
            {!readOnly && (
              <Stack direction="row" onClick={(e) => e.stopPropagation()}>
                <Tooltip title="Move up"><span><IconButton size="small" disabled={i === 0} onClick={() => onMove(i, -1)}><ArrowUpwardIcon fontSize="inherit" /></IconButton></span></Tooltip>
                <Tooltip title="Move down"><span><IconButton size="small" disabled={i === items.length - 1} onClick={() => onMove(i, 1)}><ArrowDownwardIcon fontSize="inherit" /></IconButton></span></Tooltip>
                <Tooltip title="Clone"><IconButton size="small" onClick={() => onClone(i)}><ContentCopyIcon fontSize="inherit" /></IconButton></Tooltip>
                <Tooltip title="Delete"><IconButton size="small" onClick={() => onDelete(i)}><DeleteOutlineIcon fontSize="inherit" /></IconButton></Tooltip>
              </Stack>
            )}
          </Stack>
        </ListItemButton>
      ))}
    </List>
  )
}
