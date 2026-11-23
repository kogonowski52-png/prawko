package pl.prawko.app.data.importing

import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogJsonTest {
    private val hash = "a".repeat(64)

    @Test
    fun preservesDemoDataAndHashesExactPayloadBytes() {
        val payload = demoBundle() + "\n"
        val result = parseCatalog(payload.byteInputStream())

        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) },
            result.payloadSha256,
        )
        val bundle = result.bundle
        assertEquals("DEMO", bundle.source)
        assertEquals("B", bundle.category)
        assertEquals("demo-1", bundle.datasetVersion)
        assertEquals(hash, bundle.sourceSha256)
        val question = bundle.questions.single()
        assertEquals("0007", question.id)
        assertEquals("Treść DEMO", question.prompt)
        assertEquals(listOf("B1", "B+E", "B"), question.categories)
        assertNull(question.optionA)
        assertEquals(CatalogTranslation("DE", "DEMO Frage", null, null, null), question.translations.single())
        assertEquals(
            CatalogMedia("PJM_QUESTION", "Znak DEMO.JPG", "media/$hash.jpg"),
            question.media.first(),
        )
        assertNull(question.media.last().assetPath)
    }

    @Test
    fun rejectsInconsistentQuestionTypeAndAnswer() {
        val payload = demoBundle().replace("\"correctAnswer\": \"YES\"", "\"correctAnswer\": \"A\"")
        assertThrows(IllegalArgumentException::class.java) { parseCatalog(payload.byteInputStream()) }
    }

    @Test
    fun rejectsMissingSelectedCategoryAndRepeatingIds() {
        val wrongCategory = demoBundle().replace("\"category\": \"B\"", "\"category\": \"A\"")
        val repeatedQuestion = demoBundle(questions = "${demoQuestion()}, ${demoQuestion()}")
        for (payload in listOf(wrongCategory, repeatedQuestion)) {
            assertThrows(IllegalArgumentException::class.java) { parseCatalog(payload.byteInputStream()) }
        }
    }

    @Test
    fun rejectsDuplicateKeysUnsafeMediaPathAndUnsupportedSchema() {
        val duplicate = demoBundle().replace("\"category\": \"B\"", "\"category\": \"A\", \"category\": \"B\"")
        val unsafePath = demoBundle().replace("media/$hash.jpg", "../demo.jpg")
        val unsupportedSchema = demoBundle().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        for (payload in listOf(duplicate, unsafePath, unsupportedSchema)) {
            assertThrows(IllegalArgumentException::class.java) { parseCatalog(payload.byteInputStream()) }
        }
    }

    @Test
    fun rejectsTrailingDataAndInvalidUtf8() {
        assertThrows(IOException::class.java) { parseCatalog((demoBundle() + "{}").byteInputStream()) }
        val invalidUtf8 = demoBundle().substringBefore("Treść DEMO").toByteArray() +
            byteArrayOf(0xc3.toByte(), 0x28) +
            demoBundle().substringAfter("Treść DEMO").toByteArray()
        assertThrows(IOException::class.java) { parseCatalog(invalidUtf8.inputStream()) }
    }

    @Test
    fun appliesTheSizeLimitToAllBytesIncludingTrailingWhitespace() {
        val bundle = demoBundle().toByteArray()
        assertEquals("demo-1", parseCatalog(padded(bundle, 32 * 1024 * 1024)).bundle.datasetVersion)
        assertThrows(CatalogTooLargeException::class.java) {
            parseCatalog(padded(bundle, 32 * 1024 * 1024 + 1))
        }
    }

    private fun padded(bundle: ByteArray, size: Int): InputStream {
        val trailingSpaces = object : InputStream() {
            private var remaining = size - bundle.size

            override fun read(): Int {
                if (remaining == 0) return -1
                remaining--
                return ' '.code
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (remaining == 0) return -1
                val read = minOf(length, remaining)
                buffer.fill(' '.code.toByte(), offset, offset + read)
                remaining -= read
                return read
            }
        }
        return SequenceInputStream(bundle.inputStream(), trailingSpaces)
    }

    private fun demoBundle(questions: String = demoQuestion()): String = """
        {
          "schemaVersion": 1,
          "category": "B",
          "datasetVersion": "demo-1",
          "source": "DEMO",
          "questions": [$questions],
          "sourceReference": "Dane syntetyczne DEMO",
          "sourceSha256": "$hash"
        }
    """.trimIndent()

    private fun demoQuestion(): String = """
        {
          "id": "0007",
          "prompt": "Treść DEMO",
          "points": 2,
          "type": "BASIC",
          "correctAnswer": "YES",
          "optionA": null,
          "optionB": null,
          "optionC": null,
          "categories": ["B1", "B+E", "B"],
          "translations": [{"language": "DE", "prompt": "DEMO Frage", "optionA": null, "optionB": null, "optionC": null}],
          "media": [
            {"role": "PJM_QUESTION", "originalName": "Znak DEMO.JPG", "assetPath": "media/$hash.jpg"},
            {"role": "QUESTION", "originalName": "Film DEMO.wmv", "assetPath": null}
          ]
        }
    """.trimIndent()
}
