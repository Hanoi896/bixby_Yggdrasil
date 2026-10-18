package com.yggdrasil.assistant.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * [YggdrasilLocationProvider]
 * 안드로이드 시스템 LocationManager 및 Geocoder 연동 실시간 온디바이스 위치 공급자.
 * 
 * 1. GPS 및 네트워크 프로바이더를 결합하여 현재 단말의 실시간 위도/경도 좌표 획득
 * 2. Geocoder를 통한 역지오코딩(Reverse Geocoding)으로 실제 한국어 도로명/지번 주소 자동 변환
 * 3. 클라우드 0원, 100% 온디바이스 시스템 API 연동
 */
class YggdrasilLocationProvider private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"

        @Volatile
        private var instance: YggdrasilLocationProvider? = null

        fun getInstance(context: Context): YggdrasilLocationProvider {
            return instance ?: synchronized(this) {
                instance ?: YggdrasilLocationProvider(context.applicationContext).also { instance = it }
            }
        }
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /**
     * 위치 권한 승인 여부 확인
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 현재 스마트폰의 실제 위치 및 상세 주소 조회 (비동기)
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationSummary(): String = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext "📍 위치 권한이 비활성화되어 있습니다. 스마트폰 설정에서 이그드라실의 위치 권한을 허용해주세요."
        }

        val lm = locationManager ?: return@withContext "📍 단말의 위치 서비스(LocationManager)를 사용할 수 없습니다."

        val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            return@withContext "📍 단말의 GPS(위치)가 꺼져 있습니다. 스마트폰 상단 퀵패널에서 '위치'를 켜주세요."
        }

        // 1. 가장 최신의 정확한 Location 획득 (GPS 우선, 네트워크 폴백)
        val location = getBestLastKnownLocation(lm)
        if (location == null) {
            return@withContext "📍 현재 단말의 GPS 위성 신호를 수신하는 중입니다. 잠시 후 다시 시도해 주세요."
        }

        val lat = location.latitude
        val lng = location.longitude
        val accuracy = location.accuracy

        // 2. Geocoder를 통한 한국어 상세 주소 변환
        val addressString = reverseGeocode(lat, lng)

        return@withContext if (addressString != null) {
            "📍 [현재 단말 위치]\n• 주소: $addressString\n• 좌표: 위도 ${String.format(Locale.KOREA, "%.5f", lat)}, 경도 ${String.format(Locale.KOREA, "%.5f", lng)} (오차 범위: 약 ${accuracy.toInt()}m)"
        } else {
            "📍 [현재 단말 위치 좌표]\n• 위도: ${String.format(Locale.KOREA, "%.5f", lat)}\n• 경도: ${String.format(Locale.KOREA, "%.5f", lng)} (오차 범위: 약 ${accuracy.toInt()}m)"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            if (lm.isProviderEnabled(provider)) {
                val loc = try {
                    lm.getLastKnownLocation(provider)
                } catch (e: Exception) {
                    Log.w(TAG, "위치 획득 예외 ($provider): ${e.message}")
                    null
                }
                if (loc != null) {
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }
            }
        }
        return bestLocation
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.KOREAN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var resultAddress: String? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        resultAddress = formatAddress(addr)
                    }
                    latch.countDown()
                }
                latch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
                resultAddress
            } else {
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(lat, lng, 1)
                if (!list.isNullOrEmpty()) {
                    formatAddress(list[0])
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "역지오코딩 실패: ${e.message}")
            null
        }
    }

    private fun formatAddress(addr: Address): String {
        val full = addr.getAddressLine(0)
        if (!full.isNullOrBlank()) {
            return full.replace("대한민국 ", "").trim()
        }
        val admin = addr.adminArea ?: ""
        val locality = addr.locality ?: addr.subAdminArea ?: ""
        val thoroughfare = addr.thoroughfare ?: addr.subLocality ?: ""
        val feature = addr.featureName ?: ""
        return "$admin $locality $thoroughfare $feature".trim()
    }
}
