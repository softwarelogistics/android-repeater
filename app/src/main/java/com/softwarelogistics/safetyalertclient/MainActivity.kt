package com.softwarelogistics.safetyalertclient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity() {
    // on below line we are creating variable
    // for edit text phone and message and button

    lateinit var reciever: BroadcastReceiver

    lateinit var connectLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filter = IntentFilter()
        filter.addAction("com.softwarelogistics.911repeater")

        reciever = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if(intent.hasExtra("connected")){
                    val isConnected = intent.extras!!.getBoolean("connected")
                    if(isConnected)
                        connectLabel.setText("Connected: true")
                    else
                        connectLabel.setText("Connected: false")
                }

                Log.d("onCreate", "MQTT Connected")      //UI update here
            }
        }

        connectLabel = findViewById<TextView>(R.id.lblConnectionStatus)

        registerReceiver(reciever, filter, RECEIVER_NOT_EXPORTED)

        if(checkPermissions()) {
            startBackgroundService()
        }
    }

    private fun startBackgroundService() {
        Log.d("MainActivity", "On Created - Before Background Service")
        val intent = Intent(this, MqttBackgroundService::class.java).also {intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE )
        }

        startForegroundService(intent)
        Log.d("MainActivity", "On Created - Completed")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
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

    private lateinit var mService: MqttBackgroundService
    private var mBound: Boolean = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            val binder = service as MqttBackgroundService.LocalBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

}