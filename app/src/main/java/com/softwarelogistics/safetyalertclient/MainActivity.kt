package com.softwarelogistics.safetyalertclient

import android.Manifest
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import android.view.Menu
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.softwarelogistics.safetyalertclient.com.softwarelogistics.safetyalertclient.LogListAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    // on below line we are creating variable
    // for edit text phone and message and button

    lateinit var reciever: BroadcastReceiver

    lateinit var editMqttDeviceId: EditText
    lateinit var editPhoneNumber: EditText
    lateinit var btnSaveDeviceId: Button
    lateinit var connectLabel: TextView
    lateinit var logList: ListView
    lateinit var lstAdapter: LogListAdapter
    var deviceId: String = ""
    var phoneNumber: String = ""

    private val Context.dataStore by preferencesDataStore(name = "settingsStorage")
    val DEVICE_ID = stringPreferencesKey("deviceid")
    var PHONE_NUMBER_ID = stringPreferencesKey("phonenumber")

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bottom_nav_menu, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lstAdapter = LogListAdapter(this, android.R.layout.simple_list_item_1)

        val filter = IntentFilter()
        filter.addAction("com.softwarelogistics.911repeater")

        reciever = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val index =  if(intent.hasExtra("index")) intent.extras!!.getString(("index")) else  "??"

                if(intent.hasExtra("connected")){
                    val isConnected = intent.extras!!.getBoolean("connected")
                    if(isConnected)
                        connectLabel.setText("Connected: true")
                    else
                        connectLabel.setText("Connected: false")
                }

                if(intent.hasExtra("log")) {
                    lstAdapter.addLogRecord(intent.extras!!.getString("log")!!)
                    lstAdapter.notifyDataSetChanged()
                    Log.d("Notification Received", intent.extras!!.getString("log")!!)
                }

                if(intent.hasExtra("sms")) {
                    if(phoneNumber.length > 0) {
                        val body = intent.extras!!.getString("sms")
                        val smsManager: SmsManager =   SmsManager.getDefault()
                        val piSent = PendingIntent.getBroadcast(context, 0, Intent("SMS_SENT"), FLAG_IMMUTABLE)
                        val piDelivered = PendingIntent.getBroadcast(context, 0, Intent("SMS_DELIVERED"), FLAG_IMMUTABLE)
                        smsManager.sendTextMessage(phoneNumber, null, body, piSent, piDelivered)
                        lstAdapter.addLogRecord("Sending Text to $phoneNumber - $index")
                        mService.sendMQTTTopic("repeater/$deviceId/send/$phoneNumber")
                        lstAdapter.notifyDataSetChanged()
                    }
                    else {
                        lstAdapter.addLogRecord("Phone number not configured, will not send text.")
                        lstAdapter.notifyDataSetChanged()
                    }
                }
            }
        }



        editMqttDeviceId = findViewById<EditText>(R.id.editMqttDeviceId)
        editPhoneNumber = findViewById<EditText>(R.id.editPhoneNumber)
        btnSaveDeviceId = findViewById<Button>(R.id.btnSaveDeviceId)
        connectLabel = findViewById<TextView>(R.id.lblConnectionStatus)
        logList = findViewById<ListView>(R.id.lstLog)
        logList.adapter = lstAdapter

        btnSaveDeviceId.setOnClickListener { CoroutineScope(Dispatchers.IO).launch{ saveDeviceId()} }
        registerReceiver(reciever, filter, RECEIVER_NOT_EXPORTED)
        Log.d("MAINACTIVITY_onCreate", "Register Receiver")

        smsSentReceiver = object : BroadcastReceiver() {
            override fun onReceive(arg0: Context, arg1: Intent) {
                when (resultCode) {
                    RESULT_OK -> lstAdapter.addLogRecord("SMS Send to $phoneNumber - Success")
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> lstAdapter.addLogRecord("SMS Send to $phoneNumber - Generic Failure")
                    SmsManager.RESULT_ERROR_NO_SERVICE -> lstAdapter.addLogRecord("SMS Send to $phoneNumber - No Service")
                    SmsManager.RESULT_ERROR_NULL_PDU -> lstAdapter.addLogRecord("SMS Send to $phoneNumber - No PDU")
                    SmsManager.RESULT_ERROR_RADIO_OFF -> lstAdapter.addLogRecord("SMS Send to $phoneNumber - Radio Off")

                    else -> {}
                }
            }
        }
        smsDeliveredReceiver = object : BroadcastReceiver() {
            override fun onReceive(arg0: Context, arg1: Intent) {
                // TODO Auto-generated method stub
                when (resultCode) {
                    RESULT_OK -> lstAdapter.addLogRecord("SMS Delivered $phoneNumber")
                    RESULT_CANCELED -> lstAdapter.addLogRecord("SMS Not Delivered $phoneNumber")
                }
            }
        }

        if(checkPermissions()) {
            startBackgroundService()
        }
    }

    var smsSentReceiver: BroadcastReceiver? = null
    var smsDeliveredReceiver: BroadcastReceiver? = null

    private suspend fun saveDeviceId() {
        val deviceId = editMqttDeviceId.text.toString()
        phoneNumber = editPhoneNumber.text.toString()
        dataStore.edit { preferences  -> preferences[DEVICE_ID] = deviceId; preferences[PHONE_NUMBER_ID] = phoneNumber}
        dataStore.apply {  }
        connection.getService().setMqttDeviceId(deviceId)
        mService.setOutgoingPhoneNumber(phoneNumber)
    }

    private fun startBackgroundService() {
        Log.d("MainActivity", "On Created - Before Background Service")
        val intent = Intent(this, MqttBackgroundService::class.java).also {intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE )
        }

        startForegroundService(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d("CheckPermissions", "Request Code $requestCode")
        if(checkPermissions()) {
            startBackgroundService()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun showRationaleDialog(title: String, message: String, permission: String, requestCode: Int) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message).setPositiveButton("Ok", { dialog, which ->
                requestPermissions(arrayOf(permission), requestCode)
            })
        builder.create().show()
    }

    private fun checkPermission(permission: String, rational: String, idx : Int): Boolean{
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission))
                showRationaleDialog("Required Permissions",rational,permission, idx)
            else
                ActivityCompat.requestPermissions(this,arrayOf(permission),idx)

            return false
        }
        else
            return true
    }

    fun checkPermissions(): Boolean {
        if(!checkPermission(Manifest.permission.SEND_SMS, "Need the ability to send SMS Messages to 911", 1))
            return false

        if(!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, "Access to best location available", 2))
            return false

        if(!checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, "Access the location of this 911 Repeater", 3))
            return false

        if(!checkPermission(Manifest.permission.READ_PHONE_STATE, "Access to telephony services on your phone", 4))
            return false

        if(!checkPermission(Manifest.permission.READ_SMS, "Access to SMS messages from your phone", 5))
            return false

        if(!checkPermission(Manifest.permission.READ_PHONE_NUMBERS, "Access to get the phone number of this phone", 6))
            return false

        return true
    }

    public lateinit var mService: MqttBackgroundService
    private var mBound: Boolean = false

    private val connection = object : ServiceConnection {

        fun getService() : MqttBackgroundService {
            return mService
        }

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            val binder = service as MqttBackgroundService.LocalBinder
            mService = binder.getService()
            mBound = true

            CoroutineScope(Dispatchers.IO).launch {
                val storedDeviceId = dataStore.data.first()[DEVICE_ID]
                val storedPhoneNumber = dataStore.data.first()[PHONE_NUMBER_ID]
                if(storedDeviceId != null) {
                    deviceId = storedDeviceId
                    mService.setMqttDeviceId(deviceId)
                    mService.setOutgoingPhoneNumber(phoneNumber)
                }

                if(storedPhoneNumber != null)
                    phoneNumber = storedPhoneNumber

                editPhoneNumber.setText(phoneNumber)
                editMqttDeviceId.setText(deviceId)
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

}