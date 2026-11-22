import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

// Single-series bar chart: one hue (palette slot 1), thin bars with 4px rounded
// data ends, recessive grid, hover tooltip. Title names the series, so no legend.
// Use horizontal for many categories so every label stays readable.
const SERIES_1 = '#2a78d6'
const MUTED = '#52514e'
const tick = { fontSize: 12, fill: MUTED }

export default function SimpleBarChart({ data, xKey, yKey, valueLabel, height = 260, horizontal = false }) {
  if (horizontal) {
    return (
      <ResponsiveContainer width="100%" height={Math.max(height, data.length * 26 + 30)}>
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 0 }} barCategoryGap="25%">
          <CartesianGrid horizontal={false} stroke="#e6e6e3" />
          <XAxis type="number" tick={tick} tickLine={false} axisLine={false} />
          <YAxis type="category" dataKey={xKey} tick={tick} tickLine={false} axisLine={{ stroke: '#d0d0cc' }} width={110} interval={0} />
          <Tooltip cursor={{ fill: 'rgba(42,120,214,0.08)' }} formatter={(v) => [v, valueLabel]} />
          <Bar dataKey={yKey} fill={SERIES_1} radius={[0, 4, 4, 0]} maxBarSize={16} />
        </BarChart>
      </ResponsiveContainer>
    )
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 0 }} barCategoryGap="30%">
        <CartesianGrid vertical={false} stroke="#e6e6e3" />
        <XAxis dataKey={xKey} tick={tick} tickLine={false} axisLine={{ stroke: '#d0d0cc' }} interval={0} />
        <YAxis allowDecimals={false} tick={tick} tickLine={false} axisLine={false} />
        <Tooltip cursor={{ fill: 'rgba(42,120,214,0.08)' }} formatter={(v) => [v, valueLabel]} />
        <Bar dataKey={yKey} fill={SERIES_1} radius={[4, 4, 0, 0]} maxBarSize={28} />
      </BarChart>
    </ResponsiveContainer>
  )
}
