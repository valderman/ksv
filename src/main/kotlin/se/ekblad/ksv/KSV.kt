package se.ekblad.ksv

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class CsvKey(val keyName: String)

typealias CsvTypeConverter = (String) -> Any

class CsvParser private constructor(private val config: ReadOnlyCsvParserConfiguration) {
    constructor(
        configure: CsvParserConfiguration.() -> Unit = {}
    ) : this(CsvParserConfiguration().apply(configure).freeze())
    
    private val columnParser = CsvColumnParser(config.separators, config.quotes, config.escapeCharacter)

    fun with(configure: CsvParserConfiguration.() -> Unit): CsvParser =
        CsvParser(config.thaw().apply(configure).freeze())

    inline fun <reified T : Any> readCsv(csvString: String): List<T> =
        readCsv(csvString, T::class)

    fun readCsvRows(csvString: String): List<Map<String, String>> {
        val rows = csvString.trim().split("\n")
        val header = rows.firstOrNull()
        requireNotNull(header) {
            "missing header"
        }
        val columnKeysByIndex = columnParser.splitCsvRow(header)
        return rows.drop(1).map { row ->
            val columnValues = columnParser.splitCsvRow(row)
            columnValues.mapIndexed { index, value ->
                columnKeysByIndex[index] to value
            }.toMap()
        }
    }

    fun <T : Any> readCsv(csvString: String, kClass: KClass<T>): List<T> {
        val csvRows = readCsvRows(csvString)
        val csvKeys = csvRows.firstOrNull()?.keys
        val constructor = kClass.constructors.first()

        if (csvKeys == null) {
            return emptyList()
        }

        validateConstructorAgainstCsvHeader(constructor, csvKeys)
        val columnsToInclude = findColumnsToInclude(::columnKeyFromAnnotation, constructor, csvKeys)

        return csvRows.map { row ->
            val arguments = columnsToInclude.map { (columnKey, kType) ->
                convertCsvValue(row[columnKey]!!, kType)
            }
            constructor.call(*arguments.toTypedArray())
        }
    }

    private fun <T : Any> findColumnsToInclude(
        columnKeyForParameter: (KParameter) -> String?,
        constructor: KFunction<T>,
        csvKeys: Set<String>
    ) = constructor.parameters.map { parameter ->
        val columnKey = columnKeyForParameter(parameter) ?: parameter.name
        require(columnKey in csvKeys) {
            "column name '$columnKey' not found in csv header"
        }
        columnKey to parameter.type
    }

    private fun <T : Any> validateConstructorAgainstCsvHeader(
        constructor: KFunction<T>,
        csvKeys: Set<String>
    ) {
        val unhandledTypes = constructor.parameters.filter { it.type !in config.typeConverters.converters.keys }
        require(unhandledTypes.isEmpty()) {
            val unhandledTypesString = unhandledTypes.joinToString(", ") { "'${it.type}'" }
            "no type converters registered for the following type(s): $unhandledTypesString"
        }

        require(config.ignoreUnknownColumns || csvKeys.size == constructor.parameters.size) {
            val unknownColumns = csvKeys - constructor.parameters.map(::columnKeyFromAnnotation)
            val unknownColumnsString = unknownColumns.joinToString(", ") { "'$it'" }
            "the following column(s) do not map to any field in the target type: $unknownColumnsString"
        }
    }

    private fun convertCsvValue(csvString: String, kType: KType): Any =
        (config.typeConverters.converters[kType]!!)(csvString)

    companion object {
        /**
         * Parser for comma-separated values.
         */
        val csv: CsvParser by lazy { CsvParser { separators = listOf(',') } }

        /**
         * Parser for semicolon-separated values.
         */
        val ssv: CsvParser by lazy { CsvParser { separators = listOf(';') } }

        /**
         * Parser for tab-separated values.
         */
        val tsv: CsvParser by lazy { CsvParser { separators = listOf('\t') } }

        private fun columnKeyFromAnnotation(parameter: KParameter): String? =
            parameter.annotations.filterIsInstance<CsvKey>().firstOrNull()?.keyName
    }
}
