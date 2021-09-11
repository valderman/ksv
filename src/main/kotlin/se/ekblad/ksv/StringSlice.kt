package se.ekblad.ksv

import java.lang.Integer.min

internal class StringSlice private constructor(
    private val string: String,
    private val start: Int,
    private val end: Int
) {
    override fun toString(): String = string.substring(start, end)

    val length: Int
        get() = end - start
    
    val head: Char?
        get() = string.getOrNull(start)

    val tail: StringSlice
        get() = slice(1)
    
    val indices: IntRange
        get() = 0 until length
    
    fun isNotEmpty(): Boolean =
        head != null
    
    operator fun get(index: Int) = if (index in indices) {
        string[index + start]
    } else {
        throw IndexOutOfBoundsException()
    }

    fun slice(start: Int): StringSlice =
        StringSlice(string, start = this.start + start, end)

    fun slice(start: Int, end: Int): StringSlice =
        StringSlice(string, start = this.start + start, end = min(this.start + end, this.end))

    companion object {
        fun from(string: String): StringSlice = StringSlice(string, 0, string.length)
        val empty: StringSlice = StringSlice("", 0, 0)
    }
}

internal fun String.slice(start: Int, end: Int): StringSlice =
    StringSlice.from(this).slice(start, end)

internal fun String.slice(start: Int): StringSlice =
    StringSlice.from(this).slice(start, length)
