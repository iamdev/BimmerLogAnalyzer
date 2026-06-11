package com.bimmerloganalyzer.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerloganalyzer.data.LogSession
import com.bimmerloganalyzer.data.OBDDataPoint
import com.bimmerloganalyzer.ui.components.ChartSeries
import com.bimmerloganalyzer.ui.components.OBDLineChart
import com.bimmerloganalyzer.ui.theme.*
import com.bimmerloganalyzer.viewmodel.ChartType
import com.bimmerloganalyzer.viewmodel.MainViewModel
import com.github.mikephil.charting.data.Entry
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(viewModel: MainViewModel, session: LogSession, onBack: () -> Unit) {
    val selectedChart by viewModel.selectedChartType.collectAsState()
    val points = remember(session) { session.sampledPoints() }
    val fullThrottle = remember(session) { session.fullThrottlePoints() }
    val startTime = session.startTime  // nullable LocalDateTime

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                title = {
                    Column {
                        // Primary label: datetime or filename
                        Text(
                            session.displayLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                        // Stats subtitle
                        Text(
                            "%.0f km/h · %.0f Nm · %.0f PS · %.0f RPM".format(
                                session.maxSpeedKmh, session.maxTorqueNm,
                                session.maxPowerPs, session.maxRpm,
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.FolderOpen, "เปิดไฟล์อื่น")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            ChartTypeSelector(selected = selectedChart, onSelect = viewModel::selectChart)

            StatsSummaryRow(session)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                when (selectedChart) {
                    ChartType.SPEED_TIME -> SpeedTimeChart(points, startTime)
                    ChartType.TORQUE_TIME -> TorqueTimeChart(points, startTime)
                    ChartType.POWER_TIME -> PowerTimeChart(points, startTime)
                    ChartType.DYNO_CURVE -> DynoCurveChart(fullThrottle)
                    ChartType.BOOST_TIME -> BoostTimeChart(points, startTime)
                    ChartType.TEMP_TIME -> TempTimeChart(points, startTime)
                }
            }

            // X-axis info footer
            if (startTime != null) {
                Text(
                    "แกน X = เวลาจริง  เริ่มต้น ${session.shortLabel}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 6.dp),
                )
            }
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
            ChartType.BOOST_TIME to "Boost",
            ChartType.TEMP_TIME to "Temp",
        ).forEach { (type, label) ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(label) })
        }
    }
}

@Composable
private fun StatsSummaryRow(session: LogSession) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatChip("Max Speed", "%.0f km/h".format(session.maxSpeedKmh), BluePrimary)
        StatChip("Max Torque", "%.0f Nm".format(session.maxTorqueNm), GreenAccent)
        StatChip("Max Power", "%.0f PS".format(session.maxPowerPs), OrangeAccent)
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

@Composable
private fun SpeedTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?) {
    OBDLineChart(
        series = listOf(ChartSeries("Speed (km/h)", points.map { Entry(it.time, it.speedKmh) }, AndroidColor.parseColor("#1E90FF"))),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Speed (km/h)",
        startTime = startTime,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TorqueTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Torque (Nm)", points.map { Entry(it.time, it.torqueNm) }, AndroidColor.parseColor("#00E676")),
            ChartSeries("RPM / 10", points.map { Entry(it.time, it.rpm / 10f) }, AndroidColor.parseColor("#FF6D00"), yAxisRight = true),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Torque (Nm)",
        yLabelRight = "RPM / 10",
        startTime = startTime,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PowerTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Power (PS)", points.map { Entry(it.time, it.powerPs) }, AndroidColor.parseColor("#FF6D00")),
            ChartSeries("Power (bhp)", points.map { Entry(it.time, it.powerBhp) }, AndroidColor.parseColor("#FF1744")),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Power",
        startTime = startTime,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DynoCurveChart(points: List<OBDDataPoint>) {
    if (points.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "ยังไม่มีข้อมูล Full Throttle\nกดคันเร่ง ≥ 95% เพื่อเห็น Dyno Curve",
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
            )
        }
        return
    }
    OBDLineChart(
        series = listOf(
            ChartSeries("Torque (Nm)", points.map { Entry(it.rpm, it.torqueNm) }, AndroidColor.parseColor("#00E676")),
            ChartSeries("Power (PS)", points.map { Entry(it.rpm, it.powerPs) }, AndroidColor.parseColor("#FF6D00"), yAxisRight = true),
        ),
        xLabel = "RPM",
        yLabel = "Torque (Nm)",
        yLabelRight = "Power (PS)",
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun BoostTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Boost (bar)", points.map { Entry(it.time, it.boostBar) }, AndroidColor.parseColor("#7C4DFF")),
            ChartSeries("Exhaust (bar)", points.map { Entry(it.time, it.exhaustPressureBar) }, AndroidColor.parseColor("#FF4081")),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Pressure (bar)",
        startTime = startTime,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TempTimeChart(points: List<OBDDataPoint>, startTime: LocalDateTime?) {
    OBDLineChart(
        series = listOf(
            ChartSeries("Engine (°C)", points.map { Entry(it.time, it.engineTempC) }, AndroidColor.parseColor("#FF1744")),
            ChartSeries("Trans (°C)", points.map { Entry(it.time, it.transmissionTempC) }, AndroidColor.parseColor("#FF6D00")),
            ChartSeries("Ambient (°C)", points.map { Entry(it.time, it.ambientTempC) }, AndroidColor.parseColor("#1E90FF")),
        ),
        xLabel = if (startTime != null) "เวลา" else "Time (s)",
        yLabel = "Temperature (°C)",
        startTime = startTime,
        modifier = Modifier.fillMaxSize(),
    )
}
