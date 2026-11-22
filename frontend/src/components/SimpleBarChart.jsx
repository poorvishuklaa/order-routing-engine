import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

// Single-series bar chart: one hue (palette slot 1), thin bars with 4px rounded
// data ends, recessive grid, hover tooltip. Title names the series, so no legend.
const SERIES_1 = '#2a78d6'
const MUTED = '#52514e'

export default function SimpleBarChart({ data, xKey, yKey, valueLabel, height = 260 }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 0 }} barCategoryGap="30%">
        <CartesianGrid vertical={false} stroke="#e6e6e3" />
        <XAxis dataKey={xKey} tick={{ fontSize: 12, fill: MUTED }} tickLine={false} axisLine={{ stroke: '#d0d0cc' }} interval={0} />
        <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: MUTED }} tickLine={false} axisLine={false} />
        <Tooltip cursor={{ fill: 'rgba(42,120,214,0.08)' }} formatter={(v) => [v, valueLabel]} />
        <Bar dataKey={yKey} fill={SERIES_1} radius={[4, 4, 0, 0]} maxBarSize={28} />
      </BarChart>
    </ResponsiveContainer>
  )
}
