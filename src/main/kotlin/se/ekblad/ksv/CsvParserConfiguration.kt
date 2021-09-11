package se.ekblad.ksv

import java.time.*
import kotlin.reflect.KType
import kotlin.reflect.full.createType

interface ReadOnlyTypeConverterCollection {
    val converters: Map<KType, CsvTypeConverter>
}

data class TypeConverterCollection(
    override var converters: Map<KType, CsvTypeConverter>
) : ReadOnlyTypeConverterCollection {
    inline operator fun <reified T> plusAssign(crossinline typeConverter: (String) -> T) {
        converters = converters + (T::class.createType() to { typeConverter(it) as Any })
    }
}

sealed interface ReadOnlyCsvParserConfiguration {
    val separators: Collection<Char>
    val quotes: Collection<Char>
    val escapeCharacter: Char?
    val ignoreUnknownColumns: Boolean
    val typeConverters: ReadOnlyTypeConverterCollection

    fun thaw(): CsvParserConfiguration = when (this) {
        is CsvParserConfiguration -> copy(typeConverters = typeConverters.copy())
    }
}

data class CsvParserConfiguration(
    override var separators: Collection<Char> = CsvParserDefaults.DELIMITERS,
    override var quotes: Collection<Char> = CsvParserDefaults.QUOTES,
    override val escapeCharacter: Char? = '\\',
    override var ignoreUnknownColumns: Boolean = false,
    override val typeConverters: TypeConverterCollection = TypeConverterCollection(CsvParserDefaults.TYPE_CONVERTERS)
) : ReadOnlyCsvParserConfiguration {
    fun freeze(): ReadOnlyCsvParserConfiguration = copy(typeConverters = typeConverters.copy())
}

object CsvParserDefaults {
    val QUOTES = listOf('"', '\'')
    val DELIMITERS = listOf(',')

    val TYPE_CONVERTERS = mapOf<KType, CsvTypeConverter>(
        String::class.createType() to { it },
        Int::class.createType() to { it.toInt() },
        Double::class.createType() to { it.toDouble() },
        Boolean::class.createType() to { parseCsvBoolean(it) },
        OffsetDateTime::class.createType() to { OffsetDateTime.parse(it) },
        OffsetTime::class.createType() to { OffsetTime.parse(it) },
        LocalDateTime::class.createType() to { LocalDateTime.parse(it) },
        LocalDate::class.createType() to { LocalDate.parse(it) },
        LocalTime::class.createType() to { LocalTime.parse(it) }
    )

    private fun parseCsvBoolean(string: String): Boolean = when (string.lowercase()) {
        "true", "yes", "y", "1", "t" -> true
        "false", "no", "n", "0", "f" -> false
        else -> throw IllegalArgumentException("'$string' is not a valid boolean value")
    }
}