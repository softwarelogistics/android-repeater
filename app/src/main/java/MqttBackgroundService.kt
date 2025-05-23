package com.softwarelogistics.safetyalertclient

import android.R
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Timer

const val PING_SENDER = "mqtt-ping-alarm"

class AlarmReceiver(private val mqttService: MqttBackgroundService) : BroadcastReceiver() {
     override fun onReceive(context: Context, intent: Intent) {
        Log.d("MqttBackgroundService",SystemClock.elapsedRealtime().toString() + " in AlarmReceiver onReceive()")
        mqttService.sendMQTTHeartBeat()
    }
}

class MqttBackgroundService() : Service() {
    lateinit var client: Mqtt3AsyncClient

    private val binder = LocalBinder()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var deviceId: String = ""
    private var lastLocation: Location? = null
    private var sendingPhoneNumber: String = ""

    private var createTime = Date()
    private var startTime = Date()

    private var index = 1

    private val alarmReceiver = AlarmReceiver(this)

    private var hasStarted = false

    private lateinit var pendingIntent: PendingIntent

    private fun registerReceiver() {
        val filter = IntentFilter()
        filter.addAction(PING_SENDER)
        this.registerReceiver(alarmReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    public  fun isConnected() : Boolean  {
        if(!this::client.isInitialized)
            return false;

        return client.state == MqttClientState.CONNECTED;
    }

    override fun onCreate() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            lastLocation = location;
            Log.d("MqttBackgroundService", "Got location - $location")
        }

        registerReceiver()

        val pingIntent = Intent()
        pingIntent.setAction(PING_SENDER)
        pingIntent.setPackage(baseContext.packageName)

        pendingIntent = PendingIntent.getBroadcast(applicationContext, 1000, pingIntent, PendingIntent.FLAG_IMMUTABLE)
        scheduleNext()
        sendNotification("NuvIoT - Safety Alerting - 911 Repeater Started")
        hasStarted = true
        startTime = Date()
        Log.d("MqttBackgroundService", "Background Process Started " + createTime + " " + startTime)

        return super.onCreate()
    }

    private fun scheduleNext() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        /*val nextTrigger = Date().time + (15 * 1000)
        val ac = AlarmManager.AlarmClockInfo(nextTrigger, null)
        alarmManager?.setAlarmClock(ac, pendingIntent);*/

        val delayInMilliseconds = 15 * 1000

        val nextAlarmInMilliseconds: Long = (System.currentTimeMillis() + delayInMilliseconds)

        if(Build.VERSION.SDK_INT >= 23){
            // In SDK 23 and above, dosing will prevent setExact, setExactAndAllowWhileIdle will force
            // the device to run this task whilst dosing.
            Log.d("MqttBackgroundService", "Alarm schedule using setExactAndAllowWhileIdle, next: " + delayInMilliseconds);
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= 19) {
            Log.d("MqttBackgroundService", "Alarm schedule using setExact, delay: " + delayInMilliseconds);
            alarmManager?.setExact(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
        } else {
            Log.d("MqttBackgroundService", "Alarm schedule using set, next: " + delayInMilliseconds);
            alarmManager?.set(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
        }
    }

    fun setMqttDeviceId(id: String) {
        val shouldRestart = deviceId != id
        deviceId = id
        if(shouldRestart) {
            if(this::client.isInitialized && client.state == MqttClientState.CONNECTED){
                client.disconnect()
            }

            connectToMQTT()
        }
    }

    fun setOutgoingPhoneNumber(ph: String) {
        sendingPhoneNumber = ph
    }

    fun sendMQTTTopic(topic: String) {
        if(client.state == MqttClientState.CONNECTED){
            client.publishWith()
                .topic(topic)
                .send()
        }
    }
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        fun getService(): MqttBackgroundService = this@MqttBackgroundService
    }

    @SuppressLint("MissingPermission")
    fun sendMQTTHeartBeat() {
        Log.d("MqttBackgroundService", "Hearbeat - Start:" + startTime + " Create: " + createTime)

        if(this::client.isInitialized) {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            val currentPhone = telephonyManager.line1Number
            val cellLocationList = telephonyManager.allCellInfo
            val networkOperator = telephonyManager.networkOperator

            val mcc = if (networkOperator.length > 6) networkOperator.substring(3, 0) else ""
            val mnc = if (networkOperator.length > 6) networkOperator.substring(3) else ""

            val bm = baseContext.getSystemService(BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            var charging = bm.getIntProperty(BatteryManager.BATTERY_STATUS_CHARGING)

            val topic = "nuviot/srvr/dvcsrvc/$deviceId/heartbeat"
            var payload = "phone=$currentPhone,mcc=${mcc},mnc=${mnc},"

            if (lastLocation != null)
                payload += "lat=${lastLocation!!.latitude},lon=${lastLocation!!.longitude},"

            if (sendingPhoneNumber != null && sendingPhoneNumber.length > 0)
                payload += "phone=${sendingPhoneNumber},"

            if (client.state != MqttClientState.CONNECTED) {
                Log.e("MqttBackgroundService", "MQTT Not Connected - Reconnecting")
                connectToMQTT()

                val intent = Intent()
                intent.setPackage(baseContext.packageName)
                intent.setAction("com.softwarelogistics.911repeater")
                intent.putExtra("connected", false)
                intent.putExtra("log", "disconnected")
                sendBroadcast(intent)
            }

            if (client.state == MqttClientState.CONNECTED) {
                Log.d(
                    "MqttBackgroundService",
                    "Connected - send heartbeat - Start:" + startTime + " Create: " + createTime
                )
                client.publishWith()
                    .topic(topic)
                    .payload(payload.toByteArray())
                    .send()
                    .whenComplete { publish: Mqtt3Publish?, throwable: Throwable? ->
                        if (throwable != null) {
                            Log.d("could not publish", "success")
                            // handle failure to publish
                        } else {

                            val msgIntent = Intent()
                            msgIntent.setPackage(baseContext.packageName)
                            msgIntent.setAction("com.softwarelogistics.911repeater")
                            msgIntent.putExtra("log", "Sent Heartbeat")
                            sendBroadcast(msgIntent)
                        }
                    }
            } else
                Log.e("MqttBackgroundService", "MQTT Not Connected - did not send")
        }
        else {
            val msgIntent = Intent()
            msgIntent.setPackage(baseContext.packageName)
            msgIntent.setAction("com.softwarelogistics.911repeater")
            msgIntent.putExtra("log", "Pending initialization")
            sendBroadcast(msgIntent)
        }

        scheduleNext()
    }

    override fun onDestroy() {
        Log.d("MqttBackgroundService", "Service Stopped" + startTime + " Create: " + createTime)
        if(hasStarted) {
            if (pendingIntent != null) {
                // Cancel Alarm.
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                unregisterReceiver(alarmReceiver)
            }

            hasStarted = false
        }

        return super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        Log.d("MqttBackgroundService", "On Bind was Called.")
        return binder
    }

    private fun sendNotification(messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,  /* Request code */
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fcm_default_channel" //getString(R.string.default_notification_channel_id);
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification_overlay) //drawable.splash)
                .setContentTitle("Software Logistics 911 Repeater")
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        startForeground(1, notificationBuilder.build())
    }

    private fun subscribe() {
        client.toAsync().subscribeWith()
            .topicFilter("nuviot/dvcsrvc/$deviceId/#")
            .callback { publish: Mqtt3Publish ->
                val topic = publish.topic.toString()
                val parts = topic.split('/')
                val body = String(publish.payloadAsBytes,StandardCharsets.UTF_8)
                val bodySections = body.split(';')
                Log.d("MqttBackgroundService", "Topic: $topic Message: $body")
                when(parts[3])
                {
                    "command" -> {
                        var notification = parts[parts.size - 1]

                        var phone = ""
                        var sms = ""

                        Log.d("MqttBackgroundService", "Topic: $topic Message: $body")

                        bodySections.forEach({
                            var sectionParts = it.split('=')
                            if(sectionParts[0] == "phone")
                                phone = sectionParts[1]
                            else if(sectionParts[0] == "body")
                                sms = sectionParts[1]
                        })

                        if(phone != "")
                            Log.d("MqttBackgroundService", "Send to phone: $phone")

                        Log.d("MqttBackgroundService", "Message: $sms")

                        val intent = Intent()
                        intent.setPackage(baseContext.packageName)
                        intent.setAction("com.softwarelogistics.911repeater")
                        intent.putExtra("topic",notification)
                        intent.putExtra("sms", sms)
                        intent.putExtra("phone", phone)
                        intent.putExtra("index", index.toString())
                        sendBroadcast(intent)

                        index++
                    }
                    "setproperty" -> {
                        var propertyName = parts[4]
                        Log.d("MqttBackgroundService", "Set Property Name: $propertyName")
                        if(parts[4] == "fwdnumber") {
                            var newPhoneNumber = body.split('=')[1]
                            sendingPhoneNumber = newPhoneNumber
                            Log.d("MqttBackgroundService", "New Phone Number: $newPhoneNumber")
                            val intent = Intent()
                            intent.setAction("com.softwarelogistics.911repeater")
                            intent.putExtra("toopic", "newfwdnumber")
                            intent.putExtra("newfwdnumber",sendingPhoneNumber)
                            intent.putExtra("index", index.toString())
                            sendBroadcast(intent)
                        }
                    }
                }
            }
            .send()
            .whenComplete { publish: Mqtt3SubAck?, throwable: Throwable? ->
                if (throwable != null) {
                    Log.d("subScribe", "Could not subscribe")
                } else {
                    Log.d("subScribe", "Subscribed!")
                }
            }
    }


    private var hostName: String = "primary.safealertcorp.iothost.net"

    private fun connectToMQTT() {
        client = MqttClient.builder()
            .useMqttVersion3()
            .identifier(deviceId)
            .serverHost(hostName)
            .serverPort(1883)
            .buildAsync()

        client.connectWith()

            .simpleAuth()
            .username("alerting")
            .password("4MyAlerts!".toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { publish: Mqtt3ConnAck?, throwable: Throwable? ->
                if (throwable != null) {
                    val intent = Intent()
                    intent.setPackage(baseContext.packageName)
                    intent.setAction("com.softwarelogistics.911repeater")
                    intent.putExtra("connected", false)
                    intent.putExtra("log", "could not connect")
                    sendBroadcast(intent)

                    Log.d("connectToMQTT", "Could not connect")
                } else {
                    val intent = Intent()
                    intent.setPackage(baseContext.packageName)
                    intent.setAction("com.softwarelogistics.911repeater")
                    intent.putExtra("connected", true)
                    intent.putExtra("log", "Connected to $hostName")
                    sendBroadcast(intent)

                    val topic = "nuviot/srvr/dvcsrvc/$deviceId/online"
                    val payload = "firmwareSku=SA-RPTR-ANDRD-001,firmwareVersion=" + resources.getString( com.softwarelogistics.safetyalertclient.R.string.app_version)

                    Log.d("MqttBackgroundService", "Initial: " + topic + " - " + payload)

                    client.publishWith()
                        .topic(topic)
                        .payload(payload.toByteArray())
                        .send()
                        .whenComplete { publish: Mqtt3Publish?, throwable: Throwable? ->
                            if (throwable != null) {
                                Log.d("could not publish", "success")
                                // handle failure to publish
                            } else {
                                Log.d("MqttBackgroundService", "Publish Success")
                            }
                        }
                    subscribe()

                }
            }
    }

}