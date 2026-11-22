import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Alert, Box, Button, Paper, Snackbar, Stack, TextField, Typography,
} from '@mui/material'
import Grid from '@mui/material/Grid2'
import AddIcon from '@mui/icons-material/Add'
import SaveIcon from '@mui/icons-material/Save'
import api, { errorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import PageHeader from '../components/PageHeader.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import ScheduleCard from '../components/editor/ScheduleCard.jsx'
import SortableList from '../components/editor/SortableList.jsx'
import RoutingFilters from '../components/editor/RoutingFilters.jsx'
import RuleDetails from '../components/editor/RuleDetails.jsx'
import TestDriveDialog from '../components/editor/TestDriveDialog.jsx'
import RunHistoryDialog from '../components/editor/RunHistoryDialog.jsx'
import { formatDateTime, label } from '../utils/format.js'

let keySeq = 0
const newKey = () => `k${++keySeq}`

/** Adds client-side keys so new (id-less) routings and rules can be tracked in the UI. */
function withKeys(group) {
  return {
    ...group,
    routings: group.routings.map((r) => ({ ...r, _key: newKey(), rules: r.rules.map((rule) => ({ ...rule, _key: newKey() })) })),
  }
}

function stripKeys(group) {
  return {
    ...group,
    routings: group.routings.map(({ _key, ...r }) => ({ ...r, rules: r.rules.map(({ _key: k, ...rule }) => rule) })), // eslint-disable-line no-unused-vars
  }
}

const move = (list, i, delta) => {
  const next = [...list]
  ;[next[i], next[i + delta]] = [next[i + delta], next[i]]
  return next
}

export default function RoutingGroupEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { isAdmin } = useAuth()
  const readOnly = !isAdmin

  const [saved, setSaved] = useState(null)
  const [draft, setDraft] = useState(null)
  const [meta, setMeta] = useState(null)
  const [facilityGroups, setFacilityGroups] = useState([])
  const [routingIdx, setRoutingIdx] = useState(0)
  const [ruleIdx, setRuleIdx] = useState(0)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirmRun, setConfirmRun] = useState(false)
  const [testDriveOpen, setTestDriveOpen] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)

  const load = useCallback(async () => {
    try {
      const [g, m, fg] = await Promise.all([api.get(`/routing-groups/${id}`), api.get('/meta'), api.get('/facility-groups')])
      const keyed = withKeys(g.data)
      setSaved(keyed)
      setDraft(keyed)
      setMeta(m.data)
      setFacilityGroups(fg.data)
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [id])

  useEffect(() => { load() }, [load])

  const dirty = useMemo(() => saved && draft && JSON.stringify(stripKeys(saved)) !== JSON.stringify(stripKeys(draft)), [saved, draft])

  // Warn before closing the tab with unsaved changes or while a save is running
  useEffect(() => {
    const handler = (e) => { if (dirty || busy) { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty, busy])

  if (error && !draft) return <Alert severity="error">{error}</Alert>
  if (!draft || !meta) return null

  const routings = draft.routings
  const routing = routings[routingIdx]
  const rule = routing?.rules[ruleIdx]

  const setRoutings = (next) => setDraft({ ...draft, routings: next })
  const updateRouting = (updated) => setRoutings(routings.map((r, i) => (i === routingIdx ? updated : r)))
  const setRules = (rules) => updateRouting({ ...routing, rules })
  const updateRule = (updated) => setRules(routing.rules.map((r, i) => (i === ruleIdx ? updated : r)))

  const addRouting = () => {
    setRoutings([...routings, {
      id: null, _key: newKey(), name: `Routing ${routings.length + 1}`, status: 'DRAFT', queueFacilityId: meta.queues[0]?.id ?? null,
      shippingMethods: [], priorities: [], salesChannels: [], orderSortBy: 'ORDER_DATE', rules: [],
    }])
    setRoutingIdx(routings.length)
    setRuleIdx(0)
  }

  const addRule = () => {
    setRules([...routing.rules, {
      id: null, _key: newKey(), name: `Rule ${routing.rules.length + 1}`, status: 'ACTIVE', facilityGroupId: facilityGroups[0]?.id ?? null,
      carrier: meta.carriers[0], maxDistanceMiles: null, maxTransitDays: null, safetyStock: 0, allowPartialAllocation: false,
      overrideCapacity: false, facilitySorts: ['TRANSIT_TIME'], failAction: 'NEXT_RULE', moveToQueueId: null, autoCancelDays: null,
    }])
    setRuleIdx(routing.rules.length)
  }

  const cloneRouting = (i) => {
    const src = routings[i]
    const copy = { ...src, id: null, _key: newKey(), name: `${src.name} (copy)`, status: 'DRAFT', rules: src.rules.map((r) => ({ ...r, id: null, _key: newKey() })) }
    setRoutings([...routings.slice(0, i + 1), copy, ...routings.slice(i + 1)])
  }

  const save = async () => {
    setBusy(true)
    setError('')
    try {
      const { data } = await api.put(`/routing-groups/${id}`, stripKeys(draft))
      const keyed = withKeys(data)
      setSaved(keyed)
      setDraft(keyed)
      setToast('Saved')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const runNow = async () => {
    setConfirmRun(false)
    setBusy(true)
    try {
      const { data } = await api.post(`/routing-groups/${id}/run`)
      setToast(`Run #${data.id} ${data.status}: ${data.itemsPlaced} of ${data.itemsTried} items placed`)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const scheduleChanged = (data) => {
    // Schedule/status are saved on their own; keep the user's unsaved edits
    setSaved((s) => ({ ...s, cronExpression: data.cronExpression, status: data.status, version: data.version }))
    setDraft((d) => ({ ...d, cronExpression: data.cronExpression, status: data.status, version: data.version }))
    setToast('Schedule updated')
  }

  const back = () => {
    if (!dirty || window.confirm('You have unsaved changes. Leave without saving?')) navigate('/routing-groups')
  }

  return (
    <>
      <PageHeader title={draft.name || 'Routing group'}
        subtitle={`Created ${formatDateTime(draft.createdAt)} · Updated ${formatDateTime(draft.updatedAt)}${dirty ? ' · Unsaved changes' : ''}`}
        actions={
          <Stack direction="row" spacing={1}>
            <Button onClick={back}>Back</Button>
            <Button onClick={() => setHistoryOpen(true)}>History</Button>
            <Button variant="outlined" disabled={dirty || busy} onClick={() => setTestDriveOpen(true)} title={dirty ? 'Save first' : ''}>Test drive</Button>
            {isAdmin && <Button variant="outlined" color="warning" disabled={busy} onClick={() => setConfirmRun(true)}>Run now</Button>}
            {isAdmin && <Button disabled={!dirty || busy} onClick={() => setDraft(saved)}>Discard</Button>}
            {isAdmin && <Button variant="contained" startIcon={<SaveIcon />} disabled={!dirty || busy} onClick={save}>Save</Button>}
          </Stack>
        } />
      {error && <Alert severity="error" onClose={() => setError('')} sx={{ mb: 2 }}>{error}</Alert>}

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2 }}>
            <Stack spacing={1.5}>
              <TextField label="Group name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} disabled={readOnly} />
              <TextField label="Description" value={draft.description ?? ''} multiline minRows={2}
                onChange={(e) => setDraft({ ...draft, description: e.target.value })} disabled={readOnly} />
            </Stack>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <ScheduleCard group={draft} serverTimeZone={meta.serverTimeZone} readOnly={readOnly} onChanged={scheduleChanged} />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 3 }}>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="subtitle1" fontWeight={600} sx={{ flexGrow: 1 }}>Routings</Typography>
              {isAdmin && <Button size="small" startIcon={<AddIcon />} onClick={addRouting}>Add</Button>}
            </Stack>
            <Typography variant="caption" color="text.secondary">Run one after another, top to bottom.</Typography>
            <SortableList items={routings} selected={routingIdx} readOnly={readOnly}
              onSelect={(i) => { setRoutingIdx(i); setRuleIdx(0) }}
              onMove={(i, d) => { setRoutings(move(routings, i, d)); setRoutingIdx(i + d) }}
              onClone={cloneRouting}
              onDelete={(i) => { setRoutings(routings.filter((_, j) => j !== i)); setRoutingIdx(0) }}
              secondary={(r) => (r.shippingMethods.length ? r.shippingMethods.map(label).join(', ') : 'All shipping methods')} />
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          {routing ? (
            <Stack spacing={2}>
              <Paper sx={{ p: 2 }}>
                <Typography variant="subtitle1" fontWeight={600} gutterBottom>Which orders</Typography>
                <RoutingFilters routing={routing} meta={meta} onChange={updateRouting} readOnly={readOnly} />
              </Paper>
              <Paper sx={{ p: 2 }}>
                <Stack direction="row" alignItems="center" sx={{ mb: 1 }}>
                  <Typography variant="subtitle1" fontWeight={600} sx={{ flexGrow: 1 }}>Rules</Typography>
                  {isAdmin && <Button size="small" startIcon={<AddIcon />} onClick={addRule}>Add</Button>}
                </Stack>
                <Typography variant="caption" color="text.secondary">Tried in order until every item is placed.</Typography>
                <SortableList items={routing.rules} selected={ruleIdx} readOnly={readOnly} onSelect={setRuleIdx}
                  onMove={(i, d) => { setRules(move(routing.rules, i, d)); setRuleIdx(i + d) }}
                  onClone={(i) => setRules([...routing.rules, { ...routing.rules[i], id: null, _key: newKey(), name: `${routing.rules[i].name} (copy)` }])}
                  onDelete={(i) => { setRules(routing.rules.filter((_, j) => j !== i)); setRuleIdx(0) }}
                  secondary={(r) => `${facilityGroups.find((g) => g.id === r.facilityGroupId)?.name ?? 'No group'} · then ${label(r.failAction)}`} />
                {!routing.rules.length && <Typography variant="body2" color="text.secondary">No rules yet.</Typography>}
              </Paper>
            </Stack>
          ) : (
            <Paper sx={{ p: 2 }}><Typography color="text.secondary">Add a routing to start.</Typography></Paper>
          )}
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>Where to send</Typography>
            {rule
              ? <RuleDetails rule={rule} meta={meta} facilityGroups={facilityGroups} onChange={updateRule} readOnly={readOnly} />
              : <Box><Typography color="text.secondary">Select or add a rule.</Typography></Box>}
          </Paper>
        </Grid>
      </Grid>

      <ConfirmDialog open={confirmRun} title="Run now?" confirmText="Run now" onClose={() => setConfirmRun(false)} onConfirm={runNow}
        message="This routes REAL waiting orders with the last SAVED version of this group and reserves stock." />
      <TestDriveDialog groupId={id} open={testDriveOpen} onClose={() => setTestDriveOpen(false)} />
      <RunHistoryDialog groupId={id} open={historyOpen} onClose={() => setHistoryOpen(false)} />
      <Snackbar open={!!toast} autoHideDuration={5000} onClose={() => setToast('')} message={toast} />
    </>
  )
}
