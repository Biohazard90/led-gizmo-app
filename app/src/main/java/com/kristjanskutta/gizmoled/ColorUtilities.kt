package com.kristjanskutta.gizmoled

object ColorUtilities {
    fun BGR2RGB(value: Int): Int {
        return ((value shr 16) and 0xFF) or // R
                ((value shr 0) and 0xFF00) or // G
                ((value shl 16) and 0xFF0000) // B
    }

    fun RGB2BGR(value: Int): Int {
        return ((value shr 16) and 0xFF) or // B
                ((value shr 0) and 0xFF00) or // G
                ((value shl 16) and 0xFF0000) // R
    }

    fun BytesRGB2BGR(r: Byte, g: Byte, b: Byte): Int {
        val colA = r.toUInt() and 0xFF.toUInt();
        val colB = g.toUInt() and 0xFF.toUInt();
        val colC = b.toUInt() and 0xFF.toUInt();
        var colRGB: Int = (colA or
                (colB shl 8) or
                (colC shl 16)).toInt()
        colRGB = RGB2BGR(colRGB)
        return (0xFF0000 shl 8) or colRGB
    }
}