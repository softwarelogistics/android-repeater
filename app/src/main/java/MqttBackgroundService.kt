package com.softwarelogistics.safetyalertclient

import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
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
import com.softwarelogistics.safetyalertclient.com.softwarelogistics.safetyalertclient.BaseStation
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Timer
import kotlin.concurrent.fixedRateTimer


class MqttBackgroundService() : Service() {
    private lateinit var timer: Timer
    lateinit var client: Mqtt3AsyncClient

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var deviceId: String
    var lastLocation: Location? = null

    val REQUEST_SMS_PHONE_STATE = 1
    val REQUEST_LOCATION_SERVICES_STATE = 2

    var hostIntent : Intent? = null


    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        hostIntent = intent


        Log.d("onStartCommand", "Start Command Called!")
        sendNotification("Welcome to the 911 Repeater App")

        deviceId = "repeater0001"

        timer = fixedRateTimer(name="foo", startAt = Date(), period=1000) {

            sendMQTTHeartBeat()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


            fusedLocationClient.lastLocation.addOnSuccessListener {
                location: Location? -> lastLocation = location
                Log.d("Has Fine Location", "got location")
            }

        connectToMQTT()
        return super.onStartCommand(intent, flags, startId)
    }

    @SuppressLint("MissingPermission")
    private fun sendMQTTHeartBeat() {
        //if (ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED) {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager


        val currentPhone = telephonyManager.line1Number
        val cellLocationList = telephonyManager.allCellInfo
        val networkOperator = telephonyManager.networkOperator
        val mcc = networkOperator.substring(0, 3)
        val mnc = networkOperator.substring(3)

        val topic  =   "nuviot/srvr/dvcsrvc/$deviceId/heartbeat"
        var payload = "phone=$currentPhone;mcc=${mcc};mnc=${mnc};"

        if(lastLocation != null)
            payload += "lat=${lastLocation!!.latitude};lon=${lastLocation!!.longitude}"

        Log.d("sendMQTTHeartBeat", topic + " - " + payload)

        if(this::client.isInitialized && client.state == MqttClientState.CONNECTED){
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
        }
    }

    private fun bindData(cellInfo: CellInfo): BaseStation? {
        var baseStation: BaseStation? = null
        //基站有不同信号类型：2G，3G，4G
        if (cellInfo is CellInfoWcdma) {
            //联通3G
            val cellInfoWcdma = cellInfo
            val cellIdentityWcdma = cellInfoWcdma.cellIdentity
            baseStation = BaseStation()
            baseStation.type = "WCDMA"
            baseStation.cid = cellIdentityWcdma.cid
            baseStation.lac = cellIdentityWcdma.lac
            baseStation.mcc = cellIdentityWcdma.mcc
            baseStation.mnc = cellIdentityWcdma.mnc
            baseStation.bsic_psc_pci = cellIdentityWcdma.psc
            if (cellInfoWcdma.cellSignalStrength != null) {
                baseStation.asuLevel = (cellInfoWcdma.cellSignalStrength.asuLevel) //Get the signal level as an asu value between 0..31, 99 is unknown Asu is calculated based on 3GPP RSRP.
                baseStation.signalLevel = (cellInfoWcdma.cellSignalStrength.level) //Get signal level as an int from 0..4
                baseStation.dbm = (cellInfoWcdma.cellSignalStrength.dbm) //Get the signal strength as dBm
            }
        } else if (cellInfo is CellInfoLte) {
            //4G
            val cellInfoLte = cellInfo
            val cellIdentityLte = cellInfoLte.cellIdentity
            baseStation = BaseStation()
            baseStation.type = ("LTE")
            baseStation.cid = (cellIdentityLte.ci)
            baseStation.mnc = (cellIdentityLte.mnc)
            baseStation.mcc = (cellIdentityLte.mcc)
            baseStation.lac = (cellIdentityLte.tac)
            baseStation.bsic_psc_pci = (cellIdentityLte.pci)
            if (cellInfoLte.cellSignalStrength != null) {
                baseStation.asuLevel = cellInfoLte.cellSignalStrength.asuLevel
                baseStation.signalLevel = cellInfoLte.cellSignalStrength.level
                baseStation.dbm = cellInfoLte.cellSignalStrength.dbm
            }
        } else if (cellInfo is CellInfoGsm) {
            //2G
            val cellInfoGsm = cellInfo
            val cellIdentityGsm = cellInfoGsm.cellIdentity
            baseStation = BaseStation()
            baseStation.type = ("GSM")
            baseStation.cid = (cellIdentityGsm.cid)
            baseStation.lac = (cellIdentityGsm.lac)
            baseStation.mcc = (cellIdentityGsm.mcc)
            baseStation.mnc = (cellIdentityGsm.mnc)
            baseStation.bsic_psc_pci = (cellIdentityGsm.psc)
            if (cellInfoGsm.cellSignalStrength != null) {
                baseStation.asuLevel = (cellInfoGsm.cellSignalStrength.asuLevel)
                baseStation.signalLevel = (cellInfoGsm.cellSignalStrength.level)
                baseStation.dbm = (cellInfoGsm.cellSignalStrength.dbm)
            }
        } else {
            //电信2/3G
            Log.e("MQTT", "CDMA CellInfo................................................")
        }
        return baseStation
    }

    override fun stopService(name: Intent?): Boolean {
        timer.cancel()

        Log.d("MqttBackgroundService", "Timer shutdnow")

        return super.stopService(name)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("MqttBackgroundService", "On Bind was Called.")
        return null
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
                .setContentTitle("Service test")
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
                println(
                    "Received message on topic " + publish.topic + ": " + String(
                        publish.payloadAsBytes,
                        StandardCharsets.UTF_8
                    )
                )
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

    private fun connectToMQTT() {
        client = MqttClient.builder()
            .useMqttVersion3()
            .identifier("android app")
            .serverHost("primary.safealertcorp.iothost.net")
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
                    Log.d("connectToMQTT", "Could not connect")
                } else {
                    subscribe()
                    Log.d("connectToMQTT", "Connected")
                }
            }
    }

}