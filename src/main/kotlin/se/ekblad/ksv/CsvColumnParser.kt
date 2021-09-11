package se.ekblad.ksv

internal class CsvColumnParser(
    private val separators: Collection<Char>,
    private val quotes: Collection<Char>,
    private val escapeCharacter: Char?
) {
    private val whitespace = setOf(' ', '\t') - separators - escapeCharacter - quotes

    fun splitCsvRow(csvLine: String): Array<String> {
        var nextSlice = StringSlice.from(csvLine.trim())
        val columns = mutableListOf<String>()
        val acceptableTerminators = separators + null
        do {
            val result = parseCsvColumn(skipWhitespace(nextSlice))
            columns += result.parsedString
            val trimmedSlice = skipWhitespace(result.remainingSlice)
            require(trimmedSlice.head in acceptableTerminators) {
                val delimiters = separators.joinToString(", ") { "'$it'" }
                "couldn't parse row: expected one of ${delimiters}, but got '${trimmedSlice.head}'"
            }
            nextSlice = trimmedSlice.tail
        } while(result.remainingSlice.isNotEmpty())
        return columns.toTypedArray()
    }

    private data class ParseResult(val parsedString: String, val remainingSlice: StringSlice)

    private fun parseCsvColumn(csvSlice: StringSlice): ParseResult = when (val firstChar = csvSlice.head) {
        null -> throw IllegalArgumentException("got empty string")
        in quotes -> parseQuotedCsvColumn(firstChar, csvSlice.tail)
        in separators -> ParseResult("", csvSlice.tail)
        else -> takeUntilNextUnescapedDelimiter(separators, csvSlice)
    }

    private fun parseQuotedCsvColumn(endQuote: Char, slice: StringSlice): ParseResult {
        val result = takeUntilNextUnescapedDelimiter(listOf(endQuote), slice)
        require(result.remainingSlice.head == endQuote) {
            "couldn't parse quoted column: expected '$endQuote' but got '${result.remainingSlice.head}'"
        }
        return result.copy(remainingSlice = result.remainingSlice.tail)
    }

    private fun skipWhitespace(slice: StringSlice): StringSlice {
        for (i in slice.indices) {
            if (slice[i] !in whitespace) {
                return slice.slice(i)
            }
        }
        return StringSlice.empty
    }

    private fun takeUntilNextUnescapedDelimiter(delimiters: Collection<Char>, slice: StringSlice): ParseResult {
        var nextEscapedCharIndex = -1

        fun unescapeString(slice: StringSlice) = if (nextEscapedCharIndex > -1) {
            (delimiters + '\\').fold(slice.toString()) { string, char ->
                string.replace("\\$char", "$char")
            }
        } else {
            slice.toString()
        }

        for (i in slice.indices) {
            when (slice[i]) {
                in delimiters -> {
                    if (i != nextEscapedCharIndex) {
                        return ParseResult(unescapeString(slice.slice(0, i)), slice.slice(i))
                    }
                }
                escapeCharacter -> {
                    nextEscapedCharIndex = i + 1
                }
            }
        }
        return ParseResult(unescapeString(slice), StringSlice.empty)
    }
}
