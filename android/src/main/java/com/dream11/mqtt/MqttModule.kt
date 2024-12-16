package com.d11.rn.mqtt
import com.facebook.react.bridge.Promise
import java.io.Serializable

interface MqttModule {
  fun removeMqtt(clientId: String)
  fun connectMqtt(clientId: String, options: HashMap<String, Serializable>)
  fun disconnectMqtt(clientId: String)
  fun subscribeMqtt(eventId: String, clientId: String, topic: String, qos: Integer)
  fun unsubscribeMqtt(eventId: String, clientId: String, topic: String)
  fun getConnectionStatusMqtt(clientId: String): String
  fun publishMqtt(clientId: String, topic: String, payload: String, qos: Integer,promise: Promise)
}
