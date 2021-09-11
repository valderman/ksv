import org.junit.Test
import se.ekblad.ksv.CsvKey
import se.ekblad.ksv.CsvParser
import se.ekblad.ksv.CsvParserDefaults
import java.time.*
import kotlin.reflect.full.createType
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvParserTest {
    private val ssv = """
        plain;with spaces;'quoted;separator'
        'hello;world';   "123" ; 1.23
        true;456;1e10
    """.trimIndent()

    private val ssvRows = listOf(
        mapOf("plain" to "hello;world", "with spaces" to "123", "quoted;separator" to "1.23"),
        mapOf("plain" to "true", "with spaces" to "456", "quoted;separator" to "1e10")
    )

    @Test
    fun `parser can parse string regardless of separator`() {
        listOf(',', '\t', ';', '\u0000', '試').forEach { separator ->
            val rows = CsvParser { separators = listOf(separator) }.readCsvRows(ssv.replace(';', separator))
            val expectedRows = ssvRows.map { row ->
                row.map {
                    it.key.replace(';', separator) to it.value.replace(';', separator)
                }.toMap()
            }
            assertEquals(expectedRows, rows)
        }
    }

    @Test
    fun `parser ignores leading whitespace`() {
        val row = CsvParser.csv.readCsvRows("\n\n\n     hello\n\tworld").single()
        assertEquals(mapOf("hello" to "world"), row)
    }

    @Test
    fun `parser ignores trailing whitespace`() {
        val row = CsvParser.csv.readCsvRows("hello    \t\t \n\tworld\t\t   \n\n\n   ").single()
        assertEquals(mapOf("hello" to "world"), row)
    }

    @Test
    fun `can convert all built-in types`() {
        data class Foo(
            val a: Int,
            val b: String,
            val c: Double,
            val d: Boolean,
            val e: OffsetDateTime,
            val f: OffsetTime,
            val g: LocalDateTime,
            val h: LocalDate,
            val i: LocalTime
        )

        val csv = """
            a,b,c,d,e,f,g,h,i
            1,xyz,1e10,yes,2021-01-02T03:04:00.0+01:00,01:02Z,2021-02-03T04:05,2021-03-04,06:07
        """.trimIndent()
        val foo = CsvParser.csv.readCsv<Foo>(csv).single()
        val expectedFoo = Foo(
            1,
            "xyz",
            1e10,
            true,
            OffsetDateTime.of(
                LocalDateTime.of(
                    LocalDate.of(2021, 1, 2),
                    LocalTime.of(3, 4)
                ),
                ZoneOffset.ofHours(1)
            ),
            OffsetTime.of(LocalTime.of(1, 2), ZoneOffset.UTC),
            LocalDateTime.of(
                LocalDate.of(2021, 2, 3),
                LocalTime.of(4, 5)
            ),
            LocalDate.of(2021, 3, 4),
            LocalTime.of(6, 7)
        )
        assertEquals(expectedFoo, foo)
    }

    @Test
    fun `can parse data class even if field order does not match up with column order`() {
        data class Foo(val a: Int, val b: String)
        val csv = """
            b,a
            hello,123
        """.trimIndent()

        val expectedFoo = Foo(123, "hello")
        val foo = CsvParser.csv.readCsv<Foo>(csv).single()
        assertEquals(expectedFoo, foo)
    }

    @Test
    fun `can parse data class with annotated fields`() {
        data class Foo(@CsvKey("b") val a: Int, @CsvKey("a") val b: String, val c: Boolean)
        val csv = """
            a,b,c
            hello,123,y
        """.trimIndent()

        val expectedFoo = Foo(123, "hello", true)
        val foo = CsvParser.csv.readCsv<Foo>(csv).single()
        assertEquals(expectedFoo, foo)
    }

    @Test
    fun `unknown columns are not allowed by default`() {
        data class Foo(val a: String)
        val csv = """
            a,b
            hello,123
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { CsvParser.csv.readCsv<Foo>(csv).single() }
    }

    @Test
    fun `unknown columns are allowed with the correct option`() {
        data class Foo(val a: String)
        val csv = """
            a,b
            hello,123
        """.trimIndent()

        val expectedFoo = Foo("hello")
        val foo = CsvParser.csv.with { ignoreUnknownColumns = true }.readCsv<Foo>(csv).single()
        assertEquals(expectedFoo, foo)
    }

    @Test
    fun `all fields of parsed type must map to columns`() {
        data class Foo(val a: String, val b: Int)

        assertFailsWith<IllegalArgumentException> {
            CsvParser.csv.readCsv<Foo>("a\nhello").single()
        }

        assertFailsWith<IllegalArgumentException> {
            CsvParser.csv.readCsv<Foo>("a,b\nhello,world").single()
        }
    }

    @Test
    fun `'with' does not mutate its receiver`() {
        data class Foo(val a: String)
        val csv = """
            a,b
            hello,world
        """.trimIndent()

        val originalParser = CsvParser { ignoreUnknownColumns = false }
        val newParser = originalParser.with { ignoreUnknownColumns = true }

        assertFailsWith<IllegalArgumentException> {
            originalParser.readCsv<Foo>(csv).single()
        }

        assertEquals(Foo("hello"), newParser.readCsv<Foo>(csv).single())
    }

    @Test
    fun `can configure quote chars`() {
        val csv = """
            |a|
            'hello'
        """.trimIndent()

        val row = CsvParser.csv.with { quotes = listOf('|') }.readCsvRows(csv).single()
        val expectedRow = mapOf("a" to "'hello'")
        assertEquals(expectedRow, row)
    }

    @Test
    fun `can add new type converters, without affecting original parser`() {
        data class Reversed(val string: String)
        data class Foo(val a: Reversed)
        val csv = """
            a
            hello
        """.trimIndent()

        val originalParser = CsvParser()
        val newParser = originalParser.with { typeConverters += { Reversed(it.reversed()) } }

        val foo = newParser.readCsv<Foo>(csv).single()
        val expectedFoo = Foo(Reversed("olleh"))
        assertEquals(expectedFoo, foo)
        
        assertFailsWith<IllegalArgumentException> {
            originalParser.readCsv<Foo>(csv)
        }
    }

    @Test
    fun `parser fails on mismatched quote chars`() {
        val csv = """
            "a'
            hello
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            CsvParser.csv.readCsvRows(csv)
        }
    }

    @Test
    fun `parser fails on garbage between quoted values`() {
        val csv = """
            'a' garbage, 'b'
            hello,world
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            CsvParser.csv.readCsvRows(csv)
        }
    }

    @Test
    fun `parser fails on missing end quote`() {
        val csv = """
            'a
            hello
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            CsvParser.csv.readCsvRows(csv)
        }
    }

    @Test
    fun `quote chars can be escaped`() {
        val csv = """
            '\'a\''
            hello
        """.trimIndent()
        val key = CsvParser.csv.readCsvRows(csv).single().keys.single()
        assertEquals("'a'", key)
    }
    
    @Test
    fun `separator chars can be escaped`() {
        val csv = """
            \,\,\,
            hello
        """.trimIndent()
        val key = CsvParser.csv.readCsvRows(csv).single().keys.single()
        assertEquals(",,,", key)
    }

    @Test
    fun `escape char can be escaped`() {
        val csv = """
            \\
            hello
        """.trimIndent()
        val key = CsvParser.csv.readCsvRows(csv).single().keys.single()
        assertEquals("\\", key)
    }

    @Test
    fun `one quote char can appear unescaped between two other quote chars`() {
        val csv = """
            '"a"'
            hello
        """.trimIndent()
        val key = CsvParser.csv.readCsvRows(csv).single().keys.single()
        assertEquals("\"a\"", key)
    }
}
