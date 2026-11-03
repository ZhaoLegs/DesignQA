package com.legs.designqa

data class RowShape(
    val kind: String, val id: String?, val left: Float, val top: Float,
    val width: Float, val height: Float, val isText: Boolean, val fontSp: Float?,
    val clickable: Boolean, val checkable: Boolean, val children: List<RowShape>
) {
    fun signature(): String = signature(root = true)
    private fun signature(root: Boolean): String {
        // Only normalize a single text wrapper, preserving its trailing space. Fixed-size
        // cards, icon containers and wrappers whose text did not grow retain width differences.
        val wrapsText = !root && !clickable && !checkable && children.size == 1 &&
            children.single().isText && (kind.contains("Layout") || kind.contains("Container"))
        val measuredWidth = when {
            isText -> null
            wrapsText -> width - children.single().left - children.single().width
            else -> width
        }
        return listOf(kind, id, left, top, measuredWidth, height, isText, fontSp, clickable,
            checkable, children.map { it.signature(root = false) }).toString()
    }
}

/** Consecutive rows share a representative; null denotes a standalone item. */
fun representativeRows(signatures: List<String?>): List<Pair<Int, Int>> {
    val result = mutableListOf<Pair<Int, Int>>()
    var index = 0
    while (index < signatures.size) {
        val first = index++
        val key = signatures[first]
        if (key != null) while (index < signatures.size && signatures[index] == key) index++
        result += first to (index - first)
    }
    return result
}

fun <T, K : Any> fontAnnotationRepresentatives(items: List<T>, key: (T) -> K?): List<T> {
    val seen = HashSet<K>()
    return items.filter { item -> key(item)?.let { seen.add(it) } == true }
}

data class GapGeometry(val vertical: Boolean, val pos: Int, val a: Int, val b: Int)
fun representativeGapGeometries(gaps: List<GapGeometry>) = gaps.distinct()
