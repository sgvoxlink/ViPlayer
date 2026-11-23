package com.viplayer.data

/** Естественная сортировка: «Лекция 2» идёт раньше «Лекция 10». */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a).map { it.value }.toList()
        val cb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val r = if (x[0].isDigit() && y[0].isDigit()) {
                val xs = x.trimStart('0')
                val ys = y.trimStart('0')
                if (xs.length != ys.length) xs.length - ys.length else xs.compareTo(ys)
            } else {
                x.compareTo(y, ignoreCase = true)
            }
            if (r != 0) return r
        }
        return ca.size - cb.size
    }

    val media: Comparator<MediaEntity> = Comparator { a, b -> compare(a.name, b.name) }
}
