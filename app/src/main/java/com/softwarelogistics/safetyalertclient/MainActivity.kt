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
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.telephony.SmsManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.transition.Visibility
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.softwarelogistics.safetyalertclient.com.softwarelogistics.safetyalertclient.LogListAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date


private val Context.dataStore by preferencesDataStore(name = "settingsStorage")

class MainActivity : AppCompatActivity() {
    // on below line we are creating variable
    // for edit text phone and message and button

    lateinit var reciever: BroadcastReceiver

    lateinit var onlineIndicator: ImageView
    lateinit var offLineIndicator: ImageView
    lateinit var editMqttDeviceId: EditText
    lateinit var editPhoneNumber: EditText
    lateinit var btnSaveDeviceId: Button
    lateinit var btnCancel: Button
    lateinit var logList: ListView
    lateinit var versionLabel: TextView
    lateinit var connectionStatus: TextView
    lateinit var connectionTimeStamp: TextView
    lateinit var lastHeartBeat: TextView
    lateinit var lstAdapter: LogListAdapter
    lateinit var connectionSection: LinearLayout
    var deviceId: String = ""
    var phoneNumber: String = ""

    val simpleDateFormat = SimpleDateFormat("MM/dd - HH:mm:ss")

    var startTime = Date()

    val DEVICE_ID = stringPreferencesKey("deviceid")
    var PHONE_NUMBER_ID = stringPreferencesKey("phonenumber")

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bottom_nav_menu, menu)

        return super.onCreateOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.edit_connection -> {
                // Handle the "Edit Connection" menu item click here
                connectionSection.visibility = View.VISIBLE
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lstAdapter = LogListAdapter(this,  R.layout.list_view_white_text)

        intent.setAction(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);

        val filter = IntentFilter()
        filter.addAction("com.softwarelogistics.911repeater")

        reciever = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val index =  if(intent.hasExtra("index")) intent.extras!!.getString(("index")) else  "??"

                if(intent.hasExtra("connected")){
                    val isConnected = intent.extras!!.getBoolean("connected")
                    if(isConnected) {
                        startFlashing()
                        connectionTimeStamp.visibility = View.VISIBLE
                        lastHeartBeat.visibility = View.VISIBLE

                        connectionStatus.text = "Connected"
                        connectionTimeStamp.text = "Connection as of: " + simpleDateFormat.format(Date())
                    }
                    else{
                        stopFlashing()

                        connectionTimeStamp.visibility = View.GONE
                        lastHeartBeat.visibility = View.GONE

                        connectionStatus.text = "Disconnected"
                        connectionTimeStamp.text = "-"
                    }
                }

                if(intent.hasExtra("log")) {
                    lastHeartBeat.text = "Last Activity: " + simpleDateFormat.format(Date())
                    lstAdapter.addLogRecord(intent.extras!!.getString("log")!!)
                    lstAdapter.notifyDataSetChanged()
                    Log.d("Notification Received", intent.extras!!.getString("log")!! + " " + startTime)
                }

                if(intent.hasExtra("newfwdnumber")) {
                    phoneNumber = intent.extras!!.getString("newfwdnumber")!!
                    editPhoneNumber.setText(phoneNumber)
                    runBlocking {
                        dataStore.edit { preferences ->
                            preferences[DEVICE_ID] = deviceId; preferences[PHONE_NUMBER_ID] =
                            phoneNumber
                        }
                        dataStore.apply { }
                    }
                }

                if(intent.hasExtra("sms")) {
                    var sendPhoneNumber = if(intent.hasExtra("phone")) intent.extras!!.getString("phone")!! else phoneNumber

                    Log.d("MAINACTIVITY_onCreate", "Using phone number: $sendPhoneNumber")

                    sendPhoneNumber = sendPhoneNumber.replace(" ", "")
                    sendPhoneNumber = sendPhoneNumber.replace("-", "")
                    sendPhoneNumber = sendPhoneNumber.replace("(", "")
                    sendPhoneNumber = sendPhoneNumber.replace(")", "")

                    if(sendPhoneNumber.length > 0) {
                        val body = intent.extras!!.getString("sms")
                        val smsManager: SmsManager =   SmsManager.getDefault()
                        val piSent = PendingIntent.getBroadcast(context, 0, Intent("SMS_SENT"), FLAG_IMMUTABLE)
                        val piDelivered = PendingIntent.getBroadcast(context, 0, Intent("SMS_DELIVERED"), FLAG_IMMUTABLE)
                        smsManager.sendTextMessage(sendPhoneNumber, null, body, piSent, piDelivered)
                        lstAdapter.addLogRecord("Sending Text to $sendPhoneNumber")
                        mService.sendMQTTTopic("repeater/$deviceId/send/$sendPhoneNumber.trim()")
                        lstAdapter.notifyDataSetChanged()
                    }
                    else {
                        lstAdapter.addLogRecord("Phone number not configured, will not send text.")
                        lstAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        connectionSection = findViewById<LinearLayout>(R.id.connectionSection)
        editMqttDeviceId = findViewById<EditText>(R.id.editMqttDeviceId)
        editPhoneNumber = findViewById<EditText>(R.id.editPhoneNumber)
        btnSaveDeviceId = findViewById<Button>(R.id.btnSave)
        btnCancel = findViewById<Button>(R.id.btnCancel)
        versionLabel = findViewById<TextView>(R.id.lblVersion)
        onlineIndicator = findViewById<ImageView>(R.id.online_indicator)
        offLineIndicator = findViewById<ImageView>(R.id.offline_indicator)
        lastHeartBeat = findViewById<TextView>(R.id.lblLastHeartBeat)
        connectionStatus = findViewById<TextView>(R.id.lblConnectionStatus)
        connectionTimeStamp = findViewById<TextView>(R.id.lblConnectionTimeStamp)
        versionLabel.text =  "911 Repeater - " +resources.getString( R.string.app_version)

        logList = findViewById<ListView>(R.id.lstLog)
        logList.adapter = lstAdapter

        btnSaveDeviceId.setOnClickListener { CoroutineScope(Dispatchers.IO).launch{ saveDeviceId()} }
        btnCancel.setOnClickListener { connectionSection.visibility = View.GONE }

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

    private fun startFlashing() {
        val unwrappedDrawable = AppCompatResources.getDrawable(this, R.drawable.online_indicator)
        val wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable!!)
        DrawableCompat.setTint(wrappedDrawable, Color.GREEN)

        val anim = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 1000 //You can manage the blinking time with this parameter
        anim.startOffset = 20
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        onlineIndicator.startAnimation(anim)     //     button.startAnimation(anim)

        offLineIndicator.visibility = View.GONE
        onlineIndicator.visibility = View.VISIBLE
        connectionSection.visibility = View.GONE
        logList.visibility = View.GONE
   }

    private fun stopFlashing() {
        val unwrappedDrawable = AppCompatResources.getDrawable(this, R.drawable.online_indicator)
        val wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable!!)
        DrawableCompat.setTint(wrappedDrawable, Color.RED)

        onlineIndicator.clearAnimation()
        offLineIndicator.visibility = View.VISIBLE
        onlineIndicator.visibility = View.GONE
        connectionSection.visibility = View.VISIBLE
        logList.visibility = View.VISIBLE
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
        Log.d("onRequestPermissionsResult", "Request Code $requestCode")

        for (i in permissions.indices) {
            val permission = permissions[i]
            val grantResult = grantResults[i]

            if(grantResult == PackageManager.PERMISSION_GRANTED)
            {
                Log.d("onRequestPermissionsResult", "$permission Granted");
            }
            else if(grantResult == PackageManager.PERMISSION_DENIED)
            {
                Log.d("onRequestPermissionsResult", "$permission Denied");
            }
        }

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
            Log.d("Permission Not Granted", rational);

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Log.d("Show Request Permission Rational", rational);
                showRationaleDialog("Required Permissions", rational, permission, idx)
            }
            else {
                ActivityCompat.requestPermissions(this, arrayOf(permission), idx)
                Log.d("Request Permission", rational);
            }

            return false
        }
        else {
            Log.d("Permission Granted", rational);
            return true
        }
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

        /*
        if(!checkPermission(Manifest.permission.USE_EXACT_ALARM, "Use Exact Alarm to Schedule Alarms", 7)) {
            Log.d("CheckPermissions", "Does not have schedule exact alarm.");
            return false
        }

        if(!checkPermission(Manifest.permission.SCHEDULE_EXACT_ALARM, "Schedule alarms to send heart beats", 8)) {
            Log.d("CheckPermissions", "Does not have schedule exact alarm.");
            return false
        }*/

        Log.d("CheckPermissions", "Has all permissions.");

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

                if(mService.isConnected()) {
                    startFlashing()
                }
                else {
                    stopFlashing()
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

}