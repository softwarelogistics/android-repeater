package com.softwarelogistics.safetyalertclient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
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


    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val REQUEST_SMS_PHONE_STATE = 1
        val REQUEST_LOCATION_SERVICES_STATE = 2
        val REQUEST_BACKGROUND_LOCATION_SERVICES_STATE = 3
        val REQUEST_READ_PHONE_STATE = 4
        val REQUEST_READ_PHONE_NUMBERS = 5
        val REQUEST_READ_SMS = 6

        // initializing variables of phone edt,
        // message edt and send message btn.
        phoneEdt = findViewById(R.id.idEdtPhone)
        messageEdt = findViewById(R.id.idEdtMessage)
        sendMsgBtn = findViewById(R.id.idBtnSendMessage)
        connectMQTTBtn = findViewById(R.id.btnConnectToMQTT)
        sendMqttMsgBtn = findViewById(R.id.btnSendMQTT)

        val PERMISSIONS = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        // adding on click listener for send message button.
        sendMsgBtn.setOnClickListener {
            sendSMSMessage()
        }


        Log.d("MainActivity", "On Created - Before Background Service")
        val intent = Intent(this, MqttBackgroundService::class.java)
        startForegroundService(intent)
        Log.d("MainActivity", "On Created - Completed")

        val permissionsArray = PERMISSIONS.toTypedArray<String>()

        if(!hasPermissions(this, Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )) {
            ActivityCompat.requestPermissions(this, permissionsArray, 1)
        }

    }

     fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
     }


/*
        fun checkPermissions() {



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
                        "We need permissions to send text messages as a repeater to 991",
                        Manifest.permission.SEND_SMS,
                        REQUEST_SMS_PHONE_STATE
                    );
                else
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.SEND_SMS),
                        REQUEST_SMS_PHONE_STATE
                    )
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the user to grant permission to read SMS messages
                System.out.println("Permission was originally Denied")

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                )
                    showRationaleDialog(
                        "Permission Needed",
                        "We need permissions to track where this phone is to determine it's location",
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        REQUEST_BACKGROUND_LOCATION_SERVICES_STATE
                    );
                else
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQUEST_BACKGROUND_LOCATION_SERVICES_STATE
                    )
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the user to grant permission to read SMS messages
                System.out.println("Permission was originally Denied")

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.READ_PHONE_STATE
                    )
                )
                    showRationaleDialog(
                        "Permission Needed",
                        "We need permissions to get the current phone number.",
                        Manifest.permission.READ_PHONE_STATE,
                        REQUEST_READ_PHONE_STATE
                    );
                else
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_PHONE_STATE),
                        REQUEST_READ_PHONE_STATE
                    )
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the user to grant permission to read SMS messages
                System.out.println("Permission was originally Denied")

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.READ_SMS
                    )
                )
                    showRationaleDialog(
                        "Permission Needed",
                        "We need permissions to access SMS messages.",
                        Manifest.permission.READ_SMS,
                        REQUEST_READ_SMS
                    );
                else
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_SMS),
                        REQUEST_READ_SMS
                    )
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_NUMBERS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the user to grant permission to read SMS messages
                System.out.println("Permission was originally Denied")

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.READ_PHONE_NUMBERS
                    )
                )
                    showRationaleDialog(
                        "Permission Needed",
                        "We need permissions to read phone numbers.",
                        Manifest.permission.READ_PHONE_NUMBERS,
                        REQUEST_READ_PHONE_NUMBERS
                    );
                else
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_PHONE_NUMBERS),
                        REQUEST_READ_PHONE_NUMBERS
                    )
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the user to grant permission to read SMS messages
                System.out.println("Permission was originally Denied")

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
                    showRationaleDialog(
                        "Permission Needed",
                        "We need use location services to determine where this 911 Repeater is located.",
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        REQUEST_LOCATION_SERVICES_STATE
                    );
                else
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_LOCATION_SERVICES_STATE
                    )
            }

        }
    }*/

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {


        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }



    private fun sendSMSMessage() {
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


