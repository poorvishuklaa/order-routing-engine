import { Autocomplete, MenuItem, Stack, TextField } from '@mui/material'
import { label } from '../../utils/format.js'

function MultiSelect({ title, options, value, onChange, disabled }) {
  return (
    <Autocomplete multiple size="small" options={options} value={value || []} disabled={disabled}
      getOptionLabel={label} onChange={(e, v) => onChange(v)}
      renderInput={(params) => <TextField {...params} label={title} placeholder={value?.length ? '' : 'Any'} />} />
  )
}

/** Which orders a routing picks, and in what order. Empty filter = any. */
export default function RoutingFilters({ routing, meta, onChange, readOnly }) {
  const set = (field) => (value) => onChange({ ...routing, [field]: value })
  return (
    <Stack spacing={1.5}>
      <Stack direction="row" spacing={1}>
        <TextField label="Routing name" value={routing.name} onChange={(e) => set('name')(e.target.value)} fullWidth disabled={readOnly} />
        <TextField select label="Status" value={routing.status} onChange={(e) => set('status')(e.target.value)} sx={{ width: 140 }} disabled={readOnly}>
          {meta.routingStatuses.map((s) => <MenuItem key={s} value={s}>{label(s)}</MenuItem>)}
        </TextField>
      </Stack>
      <TextField select label="Queue" value={routing.queueFacilityId ?? ''} disabled={readOnly}
        onChange={(e) => set('queueFacilityId')(e.target.value === '' ? null : e.target.value)}>
        <MenuItem value="">Any queue</MenuItem>
        {meta.queues.map((q) => <MenuItem key={q.id} value={q.id}>{q.code} — {q.name}</MenuItem>)}
      </TextField>
      <MultiSelect title="Shipping methods" options={meta.shippingMethods} value={routing.shippingMethods} onChange={set('shippingMethods')} disabled={readOnly} />
      <MultiSelect title="Priorities" options={meta.priorities} value={routing.priorities} onChange={set('priorities')} disabled={readOnly} />
      <MultiSelect title="Sales channels" options={meta.salesChannels} value={routing.salesChannels} onChange={set('salesChannels')} disabled={readOnly} />
      <TextField select label="Try orders by" value={routing.orderSortBy} onChange={(e) => set('orderSortBy')(e.target.value)} disabled={readOnly}>
        {meta.orderSorts.map((s) => <MenuItem key={s} value={s}>{label(s)}</MenuItem>)}
      </TextField>
    </Stack>
  )
}
