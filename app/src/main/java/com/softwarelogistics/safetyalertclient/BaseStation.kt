package com.softwarelogistics.safetyalertclient.com.softwarelogistics.safetyalertclient

class BaseStation {
    // Getter and Setter for all fields
    var mcc: Int = 0 // Mobile Country Code

    var mnc: Int = 0 // Mobile Network Code

    var lac: Int = 0 // Location Area Code or TAC(Tracking Area Code) for LTE

    var cid: Int = 0 // Cell Identity

    var arfcn: Int = 0 // Absolute RF Channel Number (or UMTS Absolute RF Channel Number for WCDMA)

    var bsic_psc_pci: Int = 0 /* bsic for GSM, psc for WCDMA, pci for LTE,
                                   GSM has #getPsc() but always get Integer.MAX_VALUE,
                                   psc is undefined for GSM */

    var lon: Double = 0.0 // Base station longitude

    var lat: Double = 0.0 // Base station latitude

    var asuLevel: Int = 0 /* Signal level as an asu value, asu is calculated based on 3GPP RSRP
                                   for GSM, between 0..31, 99 is unknown
                                   for WCDMA, between 0..31, 99 is unknown
                                   for LTE, between 0..97, 99 is unknown
                                   for CDMA, between 0..97, 99 is unknown */

    var signalLevel: Int = 0 // Signal level as an int from 0..4

    var dbm: Int = 0 // Signal strength as dBm

    var type: String? = null // Signal type, GSM or WCDMA or LTE or CDMA

    override fun toString(): String {
        return "BaseStation{" +
                "mcc=" + mcc +
                ", mnc=" + mnc +
                ", lac=" + lac +
                ", cid=" + cid +
                ", arfcn=" + arfcn +
                ", bsic_psc_pci=" + bsic_psc_pci +
                ", lon=" + lon +
                ", lat=" + lat +
                ", asuLevel=" + asuLevel +
                ", signalLevel=" + signalLevel +
                ", dbm=" + dbm +
                ", type='" + type + '\'' +
                '}'
    }
}