package com.huginmunin.core.iot

import android.content.Context
import android.util.Log
import com.huginmunin.core.config.ApiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class MqttResult {
    object Success : MqttResult()
    data class Error(val message: String) : MqttResult()
    data class MessageReceived(val topic: String, val payload: String) : MqttResult()
}

@Singleton
class MqttClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "MqttClientManager"
    
    // MQTT settings from centralized config
    private var brokerUrl = ApiConfig.MQTT_BROKER_URL
    private var clientId = "huginmunin_${System.currentTimeMillis()}"
    
    private var mqttClient: MqttAndroidClient? = null
    private var isConnected = false
    
    private val messageCallbacks = mutableMapOf<String, (String) -> Unit>()

    /**
     * Connect to MQTT broker
     */
    suspend fun connect(
        broker: String = brokerUrl,
        username: String? = null,
        password: String? = null
    ): MqttResult = suspendCancellableCoroutine { continuation ->
        try {
            brokerUrl = broker
            mqttClient = MqttAndroidClient(context, brokerUrl, clientId)
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = true
                connectionTimeout = ApiConfig.MQTT_CONNECTION_TIMEOUT
                keepAliveInterval = ApiConfig.MQTT_KEEP_ALIVE_INTERVAL
                
                username?.let { userName = it }
                password?.let { this.password = it.toCharArray() }
            }
            
            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "Connection lost: ${cause?.message}")
                    isConnected = false
                }
                
                override fun messageArrived(topic: String, message: MqttMessage) {
                    val payload = String(message.payload)
                    Log.d(TAG, "Message received on $topic: $payload")
                    
                    // Call registered callbacks
                    messageCallbacks[topic]?.invoke(payload)
                }
                
                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d(TAG, "Message delivery complete")
                }
            })
            
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connected to MQTT broker: $brokerUrl")
                    isConnected = true
                    continuation.resume(MqttResult.Success)
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to connect: ${exception?.message}")
                    continuation.resume(MqttResult.Error(exception?.message ?: "Connection failed"))
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MQTT connect error: ${e.message}")
            continuation.resume(MqttResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Subscribe to a topic
     */
    suspend fun subscribe(topic: String, qos: Int = 1): MqttResult = suspendCancellableCoroutine { continuation ->
        if (!isConnected) {
            continuation.resume(MqttResult.Error("Not connected"))
            return@suspendCancellableCoroutine
        }
        
        try {
            mqttClient?.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to: $topic")
                    continuation.resume(MqttResult.Success)
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to subscribe: ${exception?.message}")
                    continuation.resume(MqttResult.Error(exception?.message ?: "Subscribe failed"))
                }
            })
        } catch (e: Exception) {
            continuation.resume(MqttResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Publish message to a topic
     */
    suspend fun publish(topic: String, payload: String, qos: Int = 1, retained: Boolean = false): MqttResult =
        suspendCancellableCoroutine { continuation ->
            if (!isConnected) {
                continuation.resume(MqttResult.Error("Not connected"))
                return@suspendCancellableCoroutine
            }
            
            try {
                val message = MqttMessage(payload.toByteArray()).apply {
                    this.qos = qos
                    this.isRetained = retained
                }
                
                mqttClient?.publish(topic, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "Published to $topic: $payload")
                        continuation.resume(MqttResult.Success)
                    }
                    
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Failed to publish: ${exception?.message}")
                        continuation.resume(MqttResult.Error(exception?.message ?: "Publish failed"))
                    }
                })
            } catch (e: Exception) {
                continuation.resume(MqttResult.Error(e.message ?: "Unknown error"))
            }
        }

    /**
     * Register callback for topic messages
     */
    fun registerCallback(topic: String, callback: (String) -> Unit) {
        messageCallbacks[topic] = callback
    }

    /**
     * Disconnect from broker
     */
    fun disconnect() {
        try {
            mqttClient?.disconnect()
            isConnected = false
            Log.d(TAG, "Disconnected from MQTT broker")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect: ${e.message}")
        }
    }

    /**
     * Check connection status
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Control IoT device (convenience method)
     */
    suspend fun controlDevice(deviceId: String, command: String, value: String): MqttResult {
        val topic = "huginmunin/$deviceId/command"
        val payload = """{"command":"$command","value":"$value"}"""
        
        return publish(topic, payload)
    }
}
