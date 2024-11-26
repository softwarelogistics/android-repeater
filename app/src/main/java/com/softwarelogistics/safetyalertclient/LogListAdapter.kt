package com.softwarelogistics.safetyalertclient.com.softwarelogistics.safetyalertclient

import android.content.Context
import android.util.Log
import android.widget.ArrayAdapter
import java.text.SimpleDateFormat
import java.util.Calendar


class LogListAdapter(context: Context, resource: Int) : ArrayAdapter<String>(context, resource) {

    private val items: ArrayList<String> = ArrayList<String>(10)

    fun addLogRecord(logLine: String) {
        val simpleDateFormat = SimpleDateFormat("HH:mm:ss")
        val msg = simpleDateFormat.format( Calendar.getInstance().time) + " " + logLine
        items.add(0, msg)
        items.trimToSize()

        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): String {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

}