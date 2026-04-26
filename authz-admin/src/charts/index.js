// 공통 ECharts 임포트 (tree-shake) + Vue 컴포넌트 등록
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, GridComponent,
  LegendComponent, DataZoomComponent
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  BarChart, LineChart, PieChart,
  TitleComponent, TooltipComponent, GridComponent,
  LegendComponent, DataZoomComponent
])

export default VChart
