package com.opengps.altlocationservices

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.opengps.altlocationservices.ui.theme.AltLocationServicesTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlin.math.min

object ServiceStateTracker {
  var isServiceRunning: Boolean = false
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val allGranted = mutableStateOf(false)
        val requestPermissionBackgroundLocation =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { isGranted: Map<String, Boolean> ->
                allGranted.value =
                    isGranted[android.Manifest.permission.POST_NOTIFICATIONS] == true && isGranted[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
                requestPermissionBackgroundLocation.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            )
        } else {
            allGranted.value = true
            requestPermissionBackgroundLocation.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        setContent {
            LaunchedEffect(allGranted.value) {
                if(allGranted.value && !ServiceStateTracker.isServiceRunning) {
                    startForegroundService(Intent(this@MainActivity, GPSService::class.java))
                    ServiceStateTracker.isServiceRunning = true
                }
            }
            AltLocationServicesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(Modifier.padding(innerPadding)) {
                        if (!allGranted.value)
                            Text("This app must be granted precise location and notification permissions to provide location services")
                        else
                            MainPage()
                    }
                }
            }
        }
    }
}

@Composable
fun MainPage() {
    SelectableText("Status: ${status.value}", Modifier.padding(8.dp))
    SelectableText("Latitude: ${coords.value?.lat ?: ""}", Modifier.padding(8.dp))
    SelectableText("Longitude: ${coords.value?.lon ?: ""}", Modifier.padding(8.dp))
    SelectableText("Accuracy: ${coords.value?.acc ?: ""}", Modifier.padding(8.dp))
    SelectableText("Timestamp: ${coords.value?.timestamp ?: ""}", Modifier.padding(8.dp))
    HorizontalDivider()
    Text("Cell Towers", Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
    LazyVerticalGrid(GridCells.Fixed(6)) {
        gridText("Type")
        gridText("ID")
        gridText("MCC")
        gridText("MNC")
        gridText("LAC")
        gridText("Strength")
        for (tel in cellTowers) {
            gridText(tel.radioType)
            gridText(tel.cellId.toString())
            gridText(tel.mobileCountryCode.toString())
            gridText(tel.mobileNetworkCode.toString())
            gridText(tel.locationAreaCode.toString())
            gridText(tel.signalStrength.toString())
        }
    }
    HorizontalDivider()
    Text("WiFi Access Points", Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
    LazyVerticalGrid(GridCells.Fixed(2)) {
        gridText("MAC Address")
        gridText("Strength")
        for (ap in wifiAccessPoints) {
            gridText(ap.macAddress)
            gridText(ap.signalStrength.toString())
        }
    }
}

fun LazyGridScope.gridText(text: String) {
    item {
        Text(
            text,
            Modifier
                .border(1.dp, LocalContentColor.current)
                .padding(8.dp),
            fontSize = 11.sp
        )
    }
}

@Composable
fun SelectableText(text: String, modifier: Modifier) {
    BasicTextField(text, {}, modifier, singleLine = true,
        readOnly = true, textStyle = LocalTextStyle.current.copy(LocalContentColor.current))
}

val status = mutableStateOf("")
val coords = mutableStateOf<LocationValue?>(null)
var cellTowers by mutableStateOf(listOf<CellTower>())
var wifiAccessPoints by mutableStateOf(listOf<WifiAccessPoint>())

@Serializable
data class CellTower(
    val radioType: String,
    val mobileCountryCode: Long,
    val mobileNetworkCode: Long,
    val locationAreaCode: Long,
    val cellId: Long,
    val signalStrength: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is CellTower) return false
        return radioType == other.radioType &&
                mobileCountryCode == other.mobileCountryCode &&
                mobileNetworkCode == other.mobileNetworkCode &&
                locationAreaCode == other.locationAreaCode &&
                cellId == other.cellId
    }
}

@Serializable
data class WifiAccessPoint(
    val macAddress: String,
    val signalStrength: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (other !is WifiAccessPoint) return false
        return macAddress == other.macAddress
    }
}

@Serializable
data class Request(
    val cellTowers: List<CellTower>? = null,
    val wifiAccessPoints: List<WifiAccessPoint>? = null,
    val fallbacks: Map<String, Boolean>,
)

val anythingChanged = mutableStateOf(false)

data class LocationValue(
    val lat: Double,
    val lon: Double,
    val acc: Double,
    var timestamp: LocalDateTime
)


private const val minTimeout = 4 // 4 second interval minimum
private const val maxTimeout = 128 // 2 minutes maximum
private const val timeoutFactor = 2
var curTimeout = minTimeout

@SuppressLint("MissingPermission")
suspend fun getCellInfo(ctx: Context): LocationValue {
    val telManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager

    //from Android M up must use getAllCellInfo
    val tels = telManager.allCellInfo.filter { it.isRegistered }.filterIsInstance<CellInfoLte>().map {
            CellTower(
                "lte",
                it.cellIdentity.mccString!!.toLong(),
                it.cellIdentity.mncString!!.toLong(),
                it.cellIdentity.tac.toLong(),
                it.cellIdentity.ci.toLong(),
                it.cellSignalStrength.dbm.toLong()
            )
        }

    val scanCompleted = CompletableDeferred<Unit>()
    val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context?.unregisterReceiver(this)
            scanCompleted.complete(Unit)
        }
    }
    ctx.registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    // WifiManager.startScan() was deprecated and moved to com.google.android.gms:play-services-location
    if(@Suppress("DEPRECATION") wifiManager.startScan()) {
        scanCompleted.await()
    }
    val wifis = wifiManager.scanResults.map { WifiAccessPoint(it.BSSID, it.level) }

    anythingChanged.value = (cellTowers != tels || wifiAccessPoints != wifis)
    if (!anythingChanged.value) {
        curTimeout = min(curTimeout * timeoutFactor, maxTimeout)
        coords.value!!.timestamp = LocalDateTime.now()
        return coords.value!!
    }

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        expectSuccess = true
    }
    val resp = Request(tels, wifis, mapOf("ipf" to false))
    val response = client.post("https://api.beacondb.net/v1/geolocate") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(resp))
    }
    val res = response.bodyAsText()
    val json = JSONObject(res)
    if (!json.has("accuracy")) {
        curTimeout = min(curTimeout * timeoutFactor, maxTimeout)
        return coords.value!!
    }
    val acc = json.getDouble("accuracy")
    val lat = json.getJSONObject("location").getDouble("lat")
    val lon = json.getJSONObject("location").getDouble("lng")

    cellTowers = tels
    wifiAccessPoints = wifis
    curTimeout = if (
        coords.value != null
        &&
        coords.value!!.lat == lat
        &&
        coords.value!!.lon == lon
        &&
        coords.value!!.acc == acc
    ) {
        min(curTimeout * timeoutFactor, maxTimeout)
    } else {
        minTimeout
    }
    coords.value = LocationValue(lat, lon, acc, LocalDateTime.now())

    return coords.value!!
}

fun isMockLocationAllowed(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_MOCK_LOCATION,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun setMock(location: LocationValue, ctx: Context): Boolean {
    if (!isMockLocationAllowed(ctx)) {
        Log.w("AltLocationServices", "Location mocking not allowed")
        return false
    }
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    lm.addTestProvider(
        LocationManager.NETWORK_PROVIDER,
        false,
        false,
        false,
        false,
        true,
        true,
        true,
        ProviderProperties.POWER_USAGE_HIGH,
        ProviderProperties.ACCURACY_FINE
    )
    lm.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

    val loc = Location(LocationManager.NETWORK_PROVIDER)
    val mockLocation = Location(LocationManager.NETWORK_PROVIDER)
    mockLocation.latitude = location.lat
    mockLocation.longitude = location.lon
    mockLocation.altitude = loc.altitude
    mockLocation.time = System.currentTimeMillis()
    mockLocation.accuracy = location.acc.toFloat()
    mockLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    mockLocation.isMock = false
    lm.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, mockLocation)
    return true
}