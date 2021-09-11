import org.junit.Test
import se.ekblad.ksv.StringSlice
import se.ekblad.ksv.slice
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringSliceTest {
    @Test
    fun `unsliced slice is equivalent to the underlying string`() {
        val slice = StringSlice.from("foo").toString()
        assertEquals("foo", slice)
        assertEquals(3, slice.length)
    }

    @Test
    fun `can slice chars off start of string`() {
        val slice = "foo".slice(1).toString()
        assertEquals("oo", slice)
        assertEquals(2, slice.length)
    }

    @Test
    fun `can slice chars off end of string`() {
        val slice = "foo".slice(0, 2).toString()
        assertEquals("fo", slice)
        assertEquals(2, slice.length)
    }

    @Test
    fun `can slice chars off both ends of string`() {
        val slice = "abc".slice(1, 2).toString()
        assertEquals("b", slice)
        assertEquals(1, slice.length)
    }

    @Test
    fun `can slice chars off start of slice`() {
        val originalSlice = "abcdef".slice(1, 5)
        val slice = originalSlice.slice(1).toString()
        assertEquals("cde", slice)
        assertEquals(3, slice.length)
    }

    @Test
    fun `can slice chars off end of slice`() {
        val originalSlice = "abcdef".slice(1, 5)
        val slice = originalSlice.slice(0, 3).toString()
        assertEquals("bcd", slice)
        assertEquals(3, slice.length)
    }

    @Test
    fun `can slice chars off both ends of slice`() {
        val originalSlice = "abcdef".slice(1, 5)
        val slice = originalSlice.slice(1, 3).toString()
        assertEquals("cd", slice)
        assertEquals(2, slice.length)
    }
    
    @Test
    fun `can't slice to obtain chars beyond the end of the parent slice`() {
        val originalSlice = "abcdef".slice(1, 5)
        originalSlice.slice(0, 5).toString().let { slice ->
            assertEquals("bcde", slice)
            assertEquals(4, slice.length)
        }
        originalSlice.slice(1, 5).toString().let { slice ->
            assertEquals("cde", slice)
            assertEquals(3, slice.length)
        }
    }
    
    @Test
    fun `can index slice`() {
        val slice = "abcdef".slice(1, 5)
        "bcde".mapIndexed { index, char ->
            assertEquals(char, slice[index])
        }
    }

    @Test
    fun `can't index outside slice`() {
        val slice = "abcdef".slice(1, 5)
        assertFailsWith<IndexOutOfBoundsException> { slice[-1] }
        assertFailsWith<IndexOutOfBoundsException> { slice[4] }
    }
    
    @Test
    fun `indices returns the correct indices`() {
        assertEquals(0 until 3, StringSlice.from("abc").indices)
        assertEquals(0 until 3, "abcde".slice(1, 4).indices)
    }
}
