package com.kregosh.mtglifetracker.web.platform

// qrcode-generator (MIT), the browser's stand-in for ZXing.

external interface QrCodeJs : JsAny {
    fun addData(data: String)
    fun make()
    fun getModuleCount(): Int
    fun isDark(row: Int, col: Int): Boolean
}

@JsModule("qrcode-generator")
external fun qrcode(typeNumber: Int, errorCorrectionLevel: String): QrCodeJs
