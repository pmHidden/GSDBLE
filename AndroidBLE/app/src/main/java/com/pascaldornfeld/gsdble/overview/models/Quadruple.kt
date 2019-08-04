package com.pascaldornfeld.gsdble.overview.models

data class Quadruple<out A, out B, out C, out D>(
    val first: A, val second: B, val third: C, val forth: D
) {
    override fun toString(): String = "($first, $second, $third, $forth)"
}