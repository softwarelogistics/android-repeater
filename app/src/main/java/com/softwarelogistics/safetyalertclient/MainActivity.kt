package com.softwarelogistics.safetyalertclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.hivemq.client.internal.mqtt.message.subscribe.suback.MqttSubAck
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck
import java.nio.charset.StandardCharsets




class MainActivity : AppCompatActivity() {
    // on below line we are creating variable
    // for edit text phone and message and button
    lateinit var phoneEdt: EditText
    lateinit var messageEdt: EditText
    lateinit var sendMsgBtn: Button
    lateinit var connectMQTTBtn: Button
    lateinit var sendMqttMsgBtn: Button

    lateinit var deviceId: String

    lateinit var client: Mqtt3AsyncClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val REQUEST_SMS_PHONE_STATE = 1

        // initializing variables of phone edt,
        // message edt and send message btn.
        phoneEdt = findViewById(R.id.idEdtPhone)
        messageEdt = findViewById(R.id.idEdtMessage)
        sendMsgBtn = findViewById(R.id.idBtnSendMessage)
        connectMQTTBtn = findViewById(R.id.btnConnectToMQTT)
        sendMqttMsgBtn = findViewById(R.id.btnSendMQTT)
        connectMQTTBtn.setOnClickListener {
            connectToMQTT()
        }

        sendMqttMsgBtn.setOnClickListener {
            sendMQTTMsg()
        }

        // adding on click listener for send message button.
        sendMsgBtn.setOnClickListener {


            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the user to grant permission to read SMS messages
                System.out.println("Permission was originally Denied")

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.SEND_SMS
                    )
                )
                    showRationaleDialog(
                        "Permission Needed",
                        "We need permissions to send text messages",
                        Manifest.permission.SEND_SMS,
                        REQUEST_SMS_PHONE_STATE
                    );
                else
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.SEND_SMS),
                        REQUEST_SMS_PHONE_STATE
                    )
            } else {

                // on below line we are creating two
                // variables for phone and message
                val phoneNumber = phoneEdt.text.toString()
                val message = messageEdt.text.toString()

                // on the below line we are creating a try and catch block
                try {

                    // on below line we are initializing sms manager.
                    //as after android 10 the getDefault function no longer works
                    //so we have to check that if our android version is greater
                    //than or equal toandroid version 6.0 i.e SDK 23
                    val smsManager: SmsManager
                    if (Build.VERSION.SDK_INT >= 23) {
                        //if SDK is greater that or equal to 23 then
                        //this is how we will initialize the SmsManager
                        smsManager = this.getSystemService(SmsManager::class.java)
                    } else {
                        //if user's SDK is less than 23 then
                        //SmsManager will be initialized like this
                        smsManager = SmsManager.getDefault()
                    }

                    // on below line we are sending text message.
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)

                    // on below line we are displaying a toast message for message send,
                    Toast.makeText(applicationContext, "Message Sent", Toast.LENGTH_LONG).show()

                } catch (e: Exception) {

                    // on catch block we are displaying toast message for error.
                    Toast.makeText(
                        applicationContext,
                        "Please enter all the data.." + e.message.toString(),
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
        }
    }

    private fun subScribe() {
        client.toAsync().subscribeWith()
            .topicFilter("nuviot/dvcsrvc/repeater001//#")
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
                    subScribe()
                    Log.d("connectToMQTT", "Connected")
                }
            }
    }

    private fun sendMQTTMsg() {
        client.publishWith()
            .topic("the/topic")
            .payload("hello world".toByteArray())
            .send()
            .whenComplete { publish: Mqtt3Publish?, throwable: Throwable? ->
                if (throwable != null) {
                    // handle failure to publish
                } else {
                    // handle successful publish, e.g. logging or incrementing a metric
                }
            }

    }

    private fun showRationaleDialog(title: String, message: String, permission: String, requestCode: Int) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ok", { dialog, which ->
                requestPermissions(arrayOf(permission), requestCode)
            })
        builder.create().show()
    }


}


