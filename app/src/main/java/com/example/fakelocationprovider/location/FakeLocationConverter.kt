package com.example.fakelocationprovider.location

import java.lang.StringBuilder

class FakeLocationConverter {
    companion object {
        // Asciiコードとして扱う
        fun parseCSV(b: ByteArray): Sequence<String> {
            return sequence {
                val builder = StringBuilder()

                for (n in b) {
                    val c = n.toChar()
                    if (c == ',') {
                        yield(builder.toString())
                        builder.clear()
                    } else {
                        builder.append(c)
                    }
                }

                if (0 < builder.length) {
                    yield(builder.toString())
                }
            }
        }

        fun from(b: ByteArray): FakeLocation? {
            var lat = 0.0
            var lon = 0.0
            var alt = 0.0
            var hdop = 0.0

            var lastIndex = -1
            var toDoubleError = false
            for (n in parseCSV(b).take(4).withIndex()) {
                lastIndex = n.index

                val d = n.value.toDoubleOrNull()
                if (d == null) {
                    toDoubleError = true
                    break
                }
                when (n.index) {
                    0 -> lat = d
                    1 -> lon = d
                    2 -> alt = d
                    3 -> hdop = d
                }
            }

            if (toDoubleError) {
                return null
            }

            // 緯度、経度があれば、良いとする
            if (lastIndex <= 0) {
                return null
            }

            return FakeLocation(
                lat,
                lon,
                alt,
                hdop
            )
        }
    }
}