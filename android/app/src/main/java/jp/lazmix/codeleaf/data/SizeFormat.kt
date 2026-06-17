package jp.lazmix.codeleaf.data

/** バイト数を人が読める単位に整形する(1024 進・小数1桁)。 */
fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    val rounded = (v * 10).toLong() / 10.0
    val text = if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}" else "$rounded"
    return "$text ${units[i]}"
}
