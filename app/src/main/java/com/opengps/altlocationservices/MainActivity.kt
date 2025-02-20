package com.opengps.altlocationservices

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.SystemClock
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject

val str1 = mutableStateOf("")


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val allGranted = mutableStateOf(false)
        val requestPermissionBackgroundLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()){}
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()
            ) { isGranted: Map<String, Boolean> ->
                allGranted.value = isGranted[android.Manifest.permission.POST_NOTIFICATIONS] == true && isGranted[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
                requestPermissionBackgroundLocation.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        if(ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.POST_NOTIFICATIONS))
        } else {
            allGranted.value = true
            requestPermissionBackgroundLocation.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        setContent {
            LaunchedEffect(allGranted.value) {
                if(allGranted.value)
                    startForegroundService(Intent(this@MainActivity, GPSService::class.java))
            }
            AltLocationServicesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        Modifier
                            .padding(innerPadding)){
                        if(!allGranted.value) {
                            Text("This app must be granted precise location and notification permissions to provide location services")
                        } else {
                            SelectableText("Status: ${status.value}", Modifier.padding(8.dp))
                            SelectableText("Latitude: ${coords.value?.first?:""}", Modifier.padding(8.dp))
                            SelectableText("Longitude: ${coords.value?.second?:""}", Modifier.padding(8.dp))
                            SelectableText("Accuracy: ${accuracy.value?:""}", Modifier.padding(8.dp))

                            HorizontalDivider()

                            Text("Cell Towers", Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                            val localDensity = LocalDensity.current
                            Row(Modifier.verticalScroll(rememberScrollState())) {
                                var height by remember { mutableStateOf(0.dp) }
                                Column(Modifier.onGloballyPositioned { coordinates ->
                                    height = with(localDensity) { coordinates.size.height.toDp() }
                                }) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text("Radio Type", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    cellTowers.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.radioType)
                                    }
                                }
                                VerticalDivider(Modifier.height(height))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text("    ID    ", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    cellTowers.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.cellId.toString())
                                    }
                                }
                                VerticalDivider(Modifier.height(height))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text(" MCC ", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    cellTowers.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.mobileCountryCode.toString())
                                    }
                                }
                                VerticalDivider(Modifier.height(height))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text(" MNC ", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    cellTowers.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.mobileNetworkCode.toString())
                                    }
                                }
                                VerticalDivider(Modifier.height(height))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text("  LAC  ", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    cellTowers.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.locationAreaCode.toString())
                                    }
                                }
                                VerticalDivider(Modifier.height(height))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text("Strength", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    cellTowers.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.signalStrength.toString())
                                    }
                                }
                            }

                            HorizontalDivider()

                            Text(
                                text = "WiFi Access Points",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                            Row(Modifier.verticalScroll(rememberScrollState())) {
                                var height by remember { mutableStateOf(0.dp) }
                                Column(Modifier.onGloballyPositioned { coordinates ->
                                    height = with(localDensity) { coordinates.size.height.toDp() }
                                }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text("   MAC Address   ", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    wifiAccessPoints.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.macAddress)
                                    }
                                }
                                VerticalDivider(Modifier.height(height))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var tabWidth by remember { mutableStateOf(0.dp) }
                                    Text("Strength", Modifier.onGloballyPositioned { coordinates ->
                                        tabWidth = with(localDensity) { coordinates.size.width.toDp() }
                                    })
                                    wifiAccessPoints.value.forEach {
                                        HorizontalDivider(Modifier.width(tabWidth))
                                        Text(it.signalStrength.toString())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableText(text: String, modifier: Modifier) {
    BasicTextField(text, {}, modifier, singleLine = true, readOnly = true, textStyle = LocalTextStyle.current.copy(
        color = LocalContentColor.current,
    ))
}

val status = mutableStateOf("")
val coords = mutableStateOf<Pair<Double, Double>?>(null)
val accuracy = mutableStateOf<Double?>(null)
val cellTowers = mutableStateOf(listOf<CellTower>())
val wifiAccessPoints = mutableStateOf(listOf<WifiAccessPoint>())

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
        if(other !is CellTower) return false
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
        if(other !is WifiAccessPoint) return false
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

@SuppressLint("MissingPermission")
suspend fun getCellInfo(ctx: Context): Pair<Pair<Double, Double>, Double> {
    val tel = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    //from Android M up must use getAllCellInfo
    val tels = tel.allCellInfo.filter { it.isRegistered }.filterIsInstance<CellInfoLte>().map {
        CellTower("lte", it.cellIdentity.mccString!!.toLong(), it.cellIdentity.mncString!!.toLong(), it.cellIdentity.tac.toLong(), it.cellIdentity.ci.toLong(), it.cellSignalStrength.dbm.toLong())
    }

    val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val apList: List<ScanResult> = wifiManager.scanResults
    val wifis = apList.map { WifiAccessPoint(it.BSSID, it.level) }

    anythingChanged.value = (cellTowers.value != tels || wifiAccessPoints.value != wifis)
    if(!anythingChanged.value) {
        return coords.value!! to accuracy.value!!
    }

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }
    val resp = Request(tels, wifis, mapOf("ipf" to false))
    val response = client.post("https://api.beacondb.net/v1/geolocate") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(resp))
    }
    if (response.status != HttpStatusCode.OK) {
      throw Exception("Unexpected status code: ${response.status}")
    }
    val res = response.bodyAsText()
    val json = JSONObject(res)
    val acc = json.getDouble("accuracy")
    val lat = json.getJSONObject("location").getDouble("lat")
    val lon = json.getJSONObject("location").getDouble("lng")

    str1.value = json.toString(4) + "\n" + JSONObject(Json.encodeToString(resp)).toString(4)
    cellTowers.value = tels
    wifiAccessPoints.value = wifis
    accuracy.value = acc
    coords.value = Pair(lat, lon)
    return Pair(Pair(lat, lon), acc)
}

fun isMockLocationAllowed(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val op = AppOpsManager.OPSTR_MOCK_LOCATION
    val mode = appOps.unsafeCheckOpNoThrow(op, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun setMock(latitude: Double, longitude: Double, accuracy: Double, ctx: Context): Boolean {
    if (!isMockLocationAllowed(ctx)) {
        Log.w("AltLocationServices", "Location mocking not allowed")
        return false
    }
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val mocLocationProvider = LocationManager.NETWORK_PROVIDER

    lm.addTestProvider(
        mocLocationProvider, false, false,
        false, false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
    )
    lm.setTestProviderEnabled(mocLocationProvider, true)

    val loc = Location(mocLocationProvider)
    val mockLocation = Location(mocLocationProvider) // a string
    mockLocation.latitude = latitude // double
    mockLocation.longitude = longitude
    mockLocation.altitude = loc.altitude
    mockLocation.time = System.currentTimeMillis()
    mockLocation.accuracy = accuracy.toFloat()
    mockLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    lm.setTestProviderLocation(mocLocationProvider, mockLocation)
    return true
}
