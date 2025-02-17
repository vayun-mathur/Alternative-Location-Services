package com.opengps.altlocationservices

import android.annotation.SuppressLint
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.opengps.altlocationservices.ui.theme.AltLocationServicesTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

val str1 = mutableStateOf("")


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val allGranted = mutableStateOf(false)
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                allGranted.value = isGranted
            }
        if(ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            allGranted.value = true
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
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())){
                        if(!allGranted.value) {
                            Text("This app must be granted precise location permissions to provide location services")
                        }
                        Text(str1.value)
                    }
                }
            }
        }
    }
}

@Serializable
data class CellTower(
    val radioType: String,
    val mobileCountryCode: Long,
    val mobileNetworkCode: Long,
    val locationAreaCode: Long,
    val cellId: Long,
    val signalStrength: Long,
)
@Serializable
data class WifiAccessPoint(
    val macAddress: String,
    val signalStrength: Int? = null
)

@Serializable
data class Request(
    val cellTowers: List<CellTower>? = null,
    val wifiAccessPoints: List<WifiAccessPoint>? = null,
    val fallbacks: Map<String, Boolean>,
)

@SuppressLint("MissingPermission")
suspend fun getCellInfo(ctx: Context): Pair<Double, Double> {
    val tel = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    //from Android M up must use getAllCellInfo
    val tels = tel.allCellInfo.filter { it.isRegistered }.filterIsInstance<CellInfoLte>().map {
        CellTower("lte", it.cellIdentity.mccString!!.toLong(), it.cellIdentity.mncString!!.toLong(), it.cellIdentity.tac.toLong(), it.cellIdentity.ci.toLong(), it.cellSignalStrength.dbm.toLong())
    }

    val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val apList: List<ScanResult> = wifiManager.scanResults
    val wifis = apList.map { WifiAccessPoint(it.BSSID, it.level) }


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
    val res = response.bodyAsText()
    val json = JSONObject(res)
    val accuracy = json.getDouble("accuracy")
    val lat = json.getJSONObject("location").getDouble("lat")
    val lon = json.getJSONObject("location").getDouble("lng")

    str1.value = json.toString(4) + "\n" + JSONObject(Json.encodeToString(resp)).toString(4)

    setMock(lat, lon, accuracy, ctx)
    return Pair(lat, lon)
}

private fun setMock(latitude: Double, longitude: Double, accuracy: Double, ctx: Context) {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val mocLocationProvider = LocationManager.GPS_PROVIDER //lm.getBestProvider( criteria, true );

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
}
