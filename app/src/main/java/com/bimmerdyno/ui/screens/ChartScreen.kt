package com.bimmerdyno.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerdyno.data.DynoPoint
import com.bimmerdyno.data.LogSession
import com.bimmerdyno.data.OBDDataPoint
import com.bimmerdyno.ui.components.ChartSeries
import com.bimmerdyno.ui.components.LocalZoomAxis
import com.bimmerdyno.ui.components.OBDLineChart
import com.bimmerdyno.ui.components.ZoomAxis
import com.bimmerdyno.ui.theme.*
import com.bimmerdyno.viewmodel.ChartType
import com.bimmerdyno.viewmodel.MainViewModel
import com.bimmerdyno.viewmodel.PowerUnit
import com.github.mikephil.charting.data.Entry
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(viewModel: MainViewModel, session: LogSession, onBack: () -> Unit) {
    val selectedChart by viewModel.selectedChartType.collectAsState()
    val powerUnit by viewModel.powerUnit.collectAsState()
    val points = remember(session) { session.sampledPoints() }
    val fullThrottle = remember(session) { session.fullThrottlePoints() }
    val dynoEstimate = remember(session) { session.dynoCurve() }
    val startTime = session.startTime

    // Bumping this resets zoom/pan on the active chart
    var resetZoomKey by remember { mutableStateOf(0) }
    var zoomAxis by remember { mutableStateOf(ZoomAxis.BOTH) }
    // Reset zoom automatically when switching chart type
    LaunchedEffect(selectedChart) { resetZoomKey++ }

    val showPowerToggle = selectedChart == ChartType.POWER_TIME ||
        selectedChart == ChartType.DYNO_CURVE ||
        selectedChart == ChartType.DYNO_ESTIMATE

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = {
                    Column {
                        Text(session.displayLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text(
                            "%.0f km/h · %.0f Nm · %.0f %s · %.0f RPM".format(
                                session.maxSpeedKmh, session.maxTorqueNm,
                                session.maxPowerPs, powerUnit.label, session.maxRpm,
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.FolderOpen, "เปิดไฟล์อื่น") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            ChartTypeSelector(selected = selectedChart, onSelect = viewModel::selectChart)

            // Power unit toggle (PS / HP) — only for power-related charts
            if (showPowerToggle) {
                PowerUnitToggle(selected = powerUnit, onSelect = viewModel::selectPowerUnit)
            }

            // Zoom-axis toggle (both / X only / Y only)
            ZoomAxisToggle(selected = zoomAxis, onSelect = { zoomAxis = it; resetZoomKey++ })

            StatsSummaryRow(session, powerUnit)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                CompositionLocalProvider(LocalZoomAxis provides zoomAxis) {
                    when (selectedChart) {
                        ChartType.SPEED_TIME -> SpeedTimeChart(points, startTime, resetZoomKey)
                        ChartType.TORQUE_TIME -> TorqueTimeChart(points, startTime, resetZoomKey)
                        ChartType.POWER_TIME -> PowerTimeChart(points, startTime, powerUnit, resetZoomKey)
                        ChartType.DYNO_CURVE -> DynoCurveChart(fullThrottle, powerUnit, resetZoomKey)
                        ChartType.DYNO_ESTIMATE -> DynoEstimateChart(dynoEstimate, powerUnit, resetZoomKey)
                        ChartType.BOOST_TIME -> BoostTimeChart(points, startTime, resetZoomKey)
                        ChartType.TEMP_TIME -> TempTimeChart(points, startTime, resetZoomKey)
                    }
                }

                // Reset-zoom button (pinch/drag to zoom, tap to reset)
                SmallFloatingActionButton(
                    onClick = { resetZoomKey++ },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(Icons.Filled.ZoomOutMap, "รีเซ็ตการซูม")
                }
            }

            val footer = when {
                selectedChart == ChartType.DYNO_ESTIMATE ->
                    "เส้นทึบ = วัดได้จริง · เส้นประ = ประมาณการณ์ (interpolate) · บีบนิ้วเพื่อซูม"
                startTime != null ->
                    "แกน X = เวลาจริง เริ่ม ${session.shortLabel} · บีบนิ้วเพื่อซูม · แตะจุดเพื่อดูค่า"
                else ->
                    "บีบนิ้วเพื่อซูม · แตะจุดเพื่อดูค่า"
            }
            Text(
                footer,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun ChartTypeSelector(selected: ChartType, onSelect: (ChartType) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            ChartType.SPEED_TIME to "Speed",
            ChartType.TORQUE_TIME to "Torque",
            ChartType.POWER_TIME to "Power",
            ChartType.DYNO_CURVE to "Dyno",
            ChartType.DYNO_ESTIMATE to "Dyno+Est",
            ChartType.BOOST_TIME to "Boost",
            ChartType.TEMP_TIME to "Temp",
        ).forEach { (type, label) ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(label) })
        }
    }
}

@Composable
private fun PowerUnitToggle(selected: PowerUnit, onSelect: (PowerUnit) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("หน่วยกำลัง:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        PowerUnit.entries.forEach { unit ->
            FilterChip(
                selected = selected == unit,
                onClick = { onSelect(unit) },
                label = { Text(unit.label) },
            )
        }
    }
}

@Composable
private fun ZoomAxisToggle(selected: ZoomAxis, onSelect: (ZoomAxis) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ซูมแกน:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        listOf(
            ZoomAxis.BOTH to "ทั้งคู่",
            ZoomAxis.X_ONLY to "X",
            ZoomAxis.Y_ONLY to "Y",
        ).forEach { (axis, label) ->
            FilterChip(
                selected = selected == axis,
                onClick = { onSelect(axis) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun StatsSummaryRow(session: LogSession, powerUnit: PowerUnit) {
    val maxPower = if (powerUnit == PowerUnit.PS) session.maxPowerPs
                   else session.points.maxOfOrNull { it.powerBhp } ?: 0f
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatChip("Max Speed", "%.0f km/h".format(session.maxSpeedKmh), BluePrimary)
        StatChip("Max Torque", "%.0f Nm".format(session.maxTorqueNm), GreenAccent)
        StatChip("Max Power", "%.0f %s".format(maxPower, powerUnit.label), OrangeAccent)
        StatChip("Max RPM", "%.0f".format(session.maxRpm), RedAccent)
    }
}

@Composable
private fun StatChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
    }
}

// ── Individual charts ───────────────────────────────────────────────────────

private fun OBDDataPoint.power(unit: PowerUnit) = if (unit == PowerUnit.PS) powerPs else powerBhp

@Composable
private fun SpeedTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?, resetKey: Int) {
    OBDLineChart(
        series = listOf(ChartSeries("Speed (km/h)", points.map { Entry(it.time, it.speedKmh) }, AndroidColor.parseColor("#1E90FF"))),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Speed (km/h)",
        startTime = startTime,
        resetZoomKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TorqueTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?, resetKey: Int) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Torque (Nm)", points.map { Entry(it.time, it.torqueNm) }, AndroidColor.parseColor("#00E676")),
            ChartSeries("RPM / 10", points.map { Entry(it.time, it.rpm / 10f) }, AndroidColor.parseColor("#FF6D00"), yAxisRight = true),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Torque (Nm)",
        yLabelRight = "RPM / 10",
        startTime = startTime,
        resetZoomKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PowerTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?, unit: PowerUnit, resetKey: Int) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Power (${unit.label})", points.map { Entry(it.time, it.power(unit)) }, AndroidColor.parseColor("#FF6D00")),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Power (${unit.label})",
        startTime = startTime,
        resetZoomKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DynoCurveChart(points: List<OBDDataPoint>, unit: PowerUnit, resetKey: Int) {
    if (points.isEmpty()) {
        EmptyDyno()
        return
    }
    OBDLineChart(
        series = listOf(
            ChartSeries("Torque (Nm)", points.map { Entry(it.rpm, it.torqueNm) }, AndroidColor.parseColor("#00E676")),
            ChartSeries("Power (${unit.label})", points.map { Entry(it.rpm, it.power(unit)) }, AndroidColor.parseColor("#FF6D00"), yAxisRight = true),
        ),
        xLabel = "RPM",
        yLabel = "Torque (Nm)",
        yLabelRight = "Power (${unit.label})",
        xIsRpm = true,
        resetZoomKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DynoEstimateChart(curve: List<DynoPoint>, unit: PowerUnit, resetKey: Int) {
    if (curve.isEmpty()) {
        EmptyDyno()
        return
    }
    fun power(p: DynoPoint) = if (unit == PowerUnit.PS) p.powerPs else p.powerBhp
    val measured = curve.filter { !it.estimated }

    OBDLineChart(
        series = listOf(
            // Continuous estimate-filled envelope (dashed)
            ChartSeries("Torque (Nm) ~est", curve.map { Entry(it.rpm, it.torqueNm) },
                AndroidColor.parseColor("#00E676"), dashed = true),
            ChartSeries("Power (${unit.label}) ~est", curve.map { Entry(it.rpm, power(it)) },
                AndroidColor.parseColor("#FF6D00"), yAxisRight = true, dashed = true),
            // Measured points overlaid as circles
            ChartSeries("Torque measured", measured.map { Entry(it.rpm, it.torqueNm) },
                AndroidColor.parseColor("#00E676"), circlesOnly = true),
            ChartSeries("Power measured", measured.map { Entry(it.rpm, power(it)) },
                AndroidColor.parseColor("#FF6D00"), yAxisRight = true, circlesOnly = true),
        ),
        xLabel = "RPM",
        yLabel = "Torque (Nm)",
        yLabelRight = "Power (${unit.label})",
        xIsRpm = true,
        resetZoomKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EmptyDyno() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "ยังไม่มีข้อมูล Full Throttle\nกดคันเร่ง ≥ 95% เพื่อเห็น Dyno Curve",
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
        )
    }
}

@Composable
private fun BoostTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?, resetKey: Int) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Boost (bar)", points.map { Entry(it.time, it.boostBar) }, AndroidColor.parseColor("#7C4DFF")),
            ChartSeries("Exhaust (bar)", points.map { Entry(it.time, it.exhaustPressureBar) }, AndroidColor.parseColor("#FF4081")),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Pressure (bar)",
        startTime = startTime,
        resetZoomKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TempTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?, resetKey: Int) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Engine (°C)", points.map { Entry(it.time, it.engineTempC) }, AndroidColor.parseColor("#FF1744")),
            ChartSeries("Trans (°C)", points.map { Entry(it.time, it.transmissionTempC) }, AndroidColor.parseColor("#FF6D00")),
            ChartSeries("Ambient (°C)", points.map { Entry(it.time, it.ambientTempC) }, AndroidColor.parseColor("#1E90FF")),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Temperature (°C)",
        startTime = startTime,
        resetZoomKey = resetKey,
        modifier = Modifier.fillMaxSize(),
    )
}
