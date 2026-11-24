package com.metrolist.music.api

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterServiceTest {
    @Test
    fun `translation parsing handles fenced and short responses`() {
        assertEquals(
            listOf("uno", ""),
            parseTranslationContent("```json\n[\"uno\"]\n```", 2).getOrThrow(),
        )
    }

    @Test
    fun `translation parsing handles the structured output object shape`() {
        assertEquals(
            listOf("uno", "dos"),
            parseTranslationContent("""{"lines": ["uno", "dos"]}""", 2).getOrThrow(),
        )
    }

    @Test
    fun `pretty-printed answers parse into lines, not json fragments`() {
        val answer = "{\n  \"lines\": [\n    \"I beg of you, talk to me, my friends\",\n    \"An old photo\"\n  ]\n}"
        assertEquals(
            listOf("I beg of you, talk to me, my friends", "An old photo"),
            parseTranslationContent(answer, 2).getOrThrow(),
        )
    }

    @Test
    fun `an answer that was cut off fails instead of showing raw json`() {
        val cutOff = """{"lines": ["So far away from me", "The morning of yesterday", "I"""
        assertTrue(parseTranslationContent(cutOff, 3).isFailure)
    }

    @Test
    fun `text around the json is ignored`() {
        assertEquals(
            listOf("uno", "dos"),
            parseTranslationContent("""Here it is: {"lines": ["uno", "dos"]} Enjoy!""", 2).getOrThrow(),
        )
    }

    @Test
    fun `line breaks inside a translated line are flattened`() {
        assertEquals(
            listOf("one two", "x"),
            parseTranslationContent("""{"lines": ["one\ntwo", "x"]}""", 2).getOrThrow(),
        )
    }

    @Test
    fun `plain line answers still work`() {
        assertEquals(listOf("uno", "dos"), parseTranslationContent("uno\ndos", 2).getOrThrow())
    }

    @Test
    fun `streaming scan keeps only finished strings`() {
        val partial = scanTranslatedLines("""{"lines": ["So far \"away\"", "caf\u00e9", "The morn""")!!
        assertEquals(listOf("So far \"away\"", "café"), partial.lines)
        assertFalse(partial.complete)

        assertEquals(emptyList<String>(), scanTranslatedLines("{")!!.lines)
        assertEquals(listOf("a"), scanTranslatedLines("""["a"]""")!!.lines)
        assertTrue(scanTranslatedLines("""["a"]""")!!.complete)
        assertNull(scanTranslatedLines("just text"))
    }

    @Test
    fun `request uses structured outputs with a lines schema and strict parameter routing`() {
        val request =
            buildTranslationRequest(
                text = "one\ntwo",
                targetLanguage = "Spanish",
                model = "model",
                mode = "Translated",
                customSystemPrompt = "",
            )

        val responseFormat = request.getValue("response_format").jsonObject
        assertEquals("json_schema", responseFormat.getValue("type").jsonPrimitive.content)
        val schema = responseFormat.getValue("json_schema").jsonObject.getValue("schema").jsonObject
        assertEquals("object", schema.getValue("type").jsonPrimitive.content)
        val lines = schema.getValue("properties").jsonObject.getValue("lines").jsonObject
        assertEquals("array", lines.getValue("type").jsonPrimitive.content)
        assertEquals("string", lines.getValue("items").jsonObject.getValue("type").jsonPrimitive.content)
        assertTrue(request.getValue("provider").jsonObject.getValue("require_parameters").jsonPrimitive.boolean)
    }

    @Test
    fun `openrouter-only provider routing is omitted for direct providers`() {
        val request =
            buildTranslationRequest(
                text = "one",
                targetLanguage = "Spanish",
                model = "mercury-2",
                mode = "Translated",
                customSystemPrompt = "",
                baseUrl = "https://api.inceptionlabs.ai/v1/chat/completions",
            )

        assertTrue("provider" !in request)
        assertTrue(request.containsKey("response_format"))
    }

    @Test
    fun `streaming romanization uses the romanization prompt`() {
        val request =
            buildTranslationRequest(
                text = "東京",
                targetLanguage = "English",
                model = "model",
                mode = "Romanized",
                customSystemPrompt = "",
                stream = true,
            )

        assertTrue(request.getValue("stream").jsonPrimitive.boolean)
        assertTrue(
            request
                .getValue("messages")
                .jsonArray[1]
                .jsonObject
                .getValue("content")
                .jsonPrimitive
                .content
                .startsWith("Romanize"),
        )
    }
}
