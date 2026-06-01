package com.gmwapp.hima.utils

/**
 * Indian-numbering-system grouping (W056): groups the last 3 digits, then in
 * pairs — e.g. 4400000 -> "44,00,000", 130000 -> "1,30,000".
 * Applied to coin/balance displays (Home header, Wallet header, txn list).
 */
fun Long.toIndianFormat(): String {
    val negative = this < 0
    val digits = kotlin.math.abs(this).toString()
    if (digits.length <= 3) return if (negative) "-$digits" else digits

    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3)
    val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
    return (if (negative) "-" else "") + "$grouped,$last3"
}

fun Int.toIndianFormat(): String = this.toLong().toIndianFormat()
