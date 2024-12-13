package com.d11.rn.mqtt

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3DisconnectException
import io.reactivex.Observer
import io.reactivex.disposables.Disposable

class MqttHelper(
    private val clientId: String,
    host: String,
    port: Int,
    enableSslConfig: Boolean,
    private val emitJsiEvent: (eventId: String, payload: WritableMap) -> Unit
) {
    private lateinit var mqtt: Mqtt3RxClient
    private val subscriptionMap: HashMap<String, HashMap<String, Subscription>> = HashMap()

    companion object {
        const val CLIENT_INITIALIZE_EVENT = "client_initialize"
        const val CONNECTED_EVENT = "connected"
        const val DISCONNECTED_EVENT = "disconnected"
        const val SUBSCRIBE_SUCCESS = "subscribe_success"
        const val SUBSCRIBE_FAILED = "subscribe_failed"
        const val ERROR_EVENT = "mqtt_error"

        const val CONNECTED = "connected"
        const val CONNECTING = "connecting"
        const val DISCONNECTED = "disconnected"
    }

    init {
        Log.d("MQTT init", "clientid " + clientId + "host "+ host + "port "+ port)
        try {
            val client = Mqtt3Client.builder()
                .identifier(clientId)
                .addDisconnectedListener { disconnectedContext ->
                    val connPayload = try {
                        (disconnectedContext.cause as Mqtt3ConnAckException?)?.mqttMessage?.returnCode?.code
                    } catch (e: Exception) {
                        null
                    }

                    val disconnectPayload = try {
                        (disconnectedContext.cause as Mqtt3DisconnectException?)?.mqttMessage.hashCode()
                    } catch (e: Exception) {
                        null
                    }
                    val params = Arguments.createMap().apply {
                        putInt("reasonCode", connPayload ?: disconnectPayload ?: -1)
                    }
                    emitJsiEvent(clientId + DISCONNECTED_EVENT, params)
                }
                .addConnectedListener {
                    val params = Arguments.createMap().apply {
                        putInt("reasonCode", 0)
                    }
                    emitJsiEvent(clientId + CONNECTED_EVENT, params)
                }
                .serverHost(host)
                .serverPort(port)

          mqtt = if(enableSslConfig) {
            client
              .sslWithDefaultConfig()
              .buildRx()
          } else {
            client
              .buildRx()
          }

          if (this::mqtt.isInitialized) {
            val params = Arguments.createMap().apply {
              putBoolean("clientInit", true)
            }
            emitJsiEvent(clientId + CLIENT_INITIALIZE_EVENT, params)
          }
        } catch (e: Exception) {
            Log.e("MQTT init", "Initialization failed: ${e.message}")
            val params = Arguments.createMap().apply {
              putBoolean("clientInit", false)
              putString("errorMessage", e.message.toString())
            }
            emitJsiEvent(clientId + ERROR_EVENT, params)
        }
    }

    fun connect(options: MqttConnectOptions) {
      Log.d("MQTT Connect called", "username " + options.username)
         val disposable: Disposable = mqtt.connectWith()
            .keepAlive(options.keepAlive)
            .cleanSession(options.cleanSession)
            .simpleAuth()
            .username(options.username)
            .password(options.password)
            .applySimpleAuth()
            .applyConnect()
            .doOnSuccess { ack ->
                Log.d("MQTT Connect", "" + ack.toString())
                for ((topic, eventIdMap) in subscriptionMap) {
                    for ((eventId, subscription) in eventIdMap) {
                        subscription.disposable.dispose()
                        subscribeMqtt(eventId, topic, subscription.qos)
                    }
                }
            }
            .doOnError { error ->
                Log.e("MQTT Connect", "" + error.message + ":" + error.cause)
                val params = Arguments.createMap().apply {
                  putBoolean("clientConnected", false)
                  putString("errorMessage", error.message.toString())
                  putString("errorCause", error.cause.toString())
                }
                emitJsiEvent(clientId + ERROR_EVENT, params)
            }
            .subscribe( {},
              { throwable ->
                // This is the error handler in the subscribe method.
                // It will be called if an error occurs in the observable chain.
                Log.e("RxJava", "Error occurred in subscribe: ${throwable.message}")
              })

    }

    fun disconnectMqtt() {
        val disposable: Disposable =  mqtt.disconnect()
            .doOnComplete {
                Log.d("MQTT Disconnect", "doOnComplete") // TODO: Replace with LogWrapper when available on bridge
            }
            .doOnError { error ->
                Log.e("MQTT Disconnect", "" + error.message) // TODO: Replace with LogWrapper when available on bridge

                /**
                 * Will be triggered when disconnection failed. Ideally this can happen when connection is already disconnected.
                 */
                val params = Arguments.createMap().apply {
                    putBoolean("clientDisconnected", false)
                    putString("errorMessage", error.message.toString())
                    putInt("reasonCode", -1)
                }
                emitJsiEvent(clientId + DISCONNECTED_EVENT, params)
            }
            .subscribe( {},
              { throwable ->
                // This is the error handler in the subscribe method.
                // It will be called if an error occurs in the observable chain.
                Log.e("RxJava", "Error occurred in subscribe: ${throwable.message}")
              })
    }

    fun subscribeMqtt(eventId: String, topic: String, qos: Int) {
        val disposable: Disposable = mqtt.subscribePublishesWith()
            .topicFilter(topic)
            .qos(MqttQos.fromCode(qos) ?: MqttQos.AT_MOST_ONCE)
            .applySubscribe()
            .doOnSingle { subAck ->
                Log.e("MQTT Subscribe", "" + subAck.returnCodes.toString())

                val params = Arguments.createMap().apply {
                    putString("message", subAck.returnCodes.toString())
                    putString("topic", topic) // TODO: get from mqtt client
                    putInt("qos", qos) // TODO: get from response
                }
                emitJsiEvent(eventId + SUBSCRIBE_SUCCESS, params)
            }
            .doOnNext { publish ->
                val params = Arguments.createMap().apply {
                    putString("payload", String(publish.payloadAsBytes))
                    putString("topic", publish.topic.toString())
                    putInt("qos", publish.qos.code)
                }
                emitJsiEvent(eventId, params)
            }
            .doOnError { error ->
                Log.e("MQTT Subscribe", "" + error.message) // TODO: Replace with LogWrapper when available on bridge
                val params = Arguments.createMap().apply {
                    putBoolean("clientSubscribed", false)
                    putString("errorMessage", error.message.toString())
                }
                emitJsiEvent(eventId + SUBSCRIBE_FAILED, params)
            }
            .subscribe( {},
              { throwable ->
                // This is the error handler in the subscribe method.
                // It will be called if an error occurs in the observable chain.
                Log.e("RxJava", "Error occurred in subscribe: ${throwable.message}")
              })

        if (!subscriptionMap.containsKey(topic)) {
            subscriptionMap[topic] = HashMap()
        }
        subscriptionMap[topic]?.set(eventId, Subscription(disposable, qos))
    }

    fun unsubscribeMqtt(eventId: String, topic: String) {
        val allSubscriptions = subscriptionMap[topic]
        if (allSubscriptions == null) {
            Log.e("MQTT Unsubscribe", "unable to unsubscribe topic: $topic")
            return
        }
        allSubscriptions[eventId]?.disposable?.dispose()
        if (allSubscriptions.size > 1) {
            allSubscriptions.remove(eventId)
        } else {
            val disposable: Disposable = mqtt.unsubscribeWith()
                .addTopicFilter(topic)
                .applyUnsubscribe()
                .doOnComplete {
            // Unsubscribe success
            Log.e("MQTT Unsubscribe", "Successfully unsubscribed from topic: $topic")
          }
                .doOnError { error ->
                    // TODO: Replace with LogWrapper when available on bridge
                    Log.e("MQTT Unsubscribe", "" + error.message)
                    val params = Arguments.createMap().apply {
                      putBoolean("clientUnsubscribed", false)
                      putString("errorMessage", error.message.toString())
                    }
                    emitJsiEvent(clientId + ERROR_EVENT, params)
                }
                .subscribe( {},
                  { throwable ->
                    // This is the error handler in the subscribe method.
                    // It will be called if an error occurs in the observable chain.
                    Log.e("RxJava", "Error occurred in subscribe: ${throwable.message}")
                  })
            subscriptionMap.remove(topic)
        }
    }

    fun getConnectionStatusMqtt(): String {
        return when(mqtt.state) {
            MqttClientState.CONNECTED -> {CONNECTED}
            MqttClientState.DISCONNECTED -> {DISCONNECTED}
            MqttClientState.CONNECTING -> {CONNECTING}
            MqttClientState.DISCONNECTED_RECONNECT -> {DISCONNECTED}
            MqttClientState.CONNECTING_RECONNECT -> {CONNECTING}
        }
    }
}

data class Subscription(
    val disposable: Disposable,
    val qos: Int
)


