package com.softwarelogistics.safetyalertclient

import android.content.Intent

data class Preferences {
    lateinit var HostName: String
    var Port: Int = 1883
    lateinit var UserName: String
    lateinit var Password: String
    lateinit var DeviceId: String
    lateinit var DestinationPhoneNumber: String
}