package dev.cpsaul.noticias

import dev.cpsaul.noticias.data.local.FeedEntity
import dev.cpsaul.noticias.data.local.ImageDiskCache
import dev.cpsaul.noticias.data.remote.FeedParser
import dev.cpsaul.noticias.data.remote.OpmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeedAndOpmlTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parsesRss20FeedAndExtractsMarkdown() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Noticias Ciencia</title>
                <item>
                  <title>Descubrimiento solar en Europa</title>
                  <link>https://example.org/solar</link>
                  <guid>solar-1</guid>
                  <pubDate>Mon, 09 Nov 2026 10:00:00 +0000</pubDate>
                  <description><![CDATA[<p>La energía solar alcanza un 62 % en Europa.</p><img src="https://example.org/img.jpg"/>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rss)
        assertEquals("Noticias Ciencia", parsed.title)
        assertEquals(1, parsed.items.size)
        assertEquals("https://example.org/img.jpg", parsed.items[0].imageUrl)

        val entities = FeedParser.toArticleEntities("f1", parsed.items)
        assertEquals(1, entities.size)
        assertTrue(entities[0].contentMarkdown.contains("62 %"))
    }

    @Test
    fun parsesAtom10Feed() {
        val atom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Tech</title>
              <entry>
                <id>urn:uuid:1234</id>
                <title>Android Material 3 Expressive</title>
                <link rel="alternate" href="https://example.org/m3"/>
                <updated>2026-11-10T12:00:00Z</updated>
                <author><name>Equipo Editorial</name></author>
                <summary>Guía completa de movimiento expresivo en Compose.</summary>
              </entry>
            </feed>
        """.trimIndent()

        val parsed = FeedParser.parse(atom)
        assertEquals("Atom Tech", parsed.title)
        assertEquals(1, parsed.items.size)
        assertEquals("Equipo Editorial", parsed.items[0].author)
        assertEquals("https://example.org/m3", parsed.items[0].url)
    }

    @Test
    fun parsesJsonFeed11() {
        val jsonFeed = """
            {
              "version": "https://jsonfeed.org/version/1.1",
              "title": "Pulso JSON",
              "items": [
                {
                  "id": "jf-1",
                  "url": "https://example.org/jf-1",
                  "title": "Verificación abierta con ClaimReview",
                  "content_html": "<p>Catálogo local offline para combatir la desinformación.</p>",
                  "date_published": "2026-11-12T08:30:00Z",
                  "authors": [{"name": "Redacción"}]
                }
              ]
            }
        """.trimIndent()

        val parsed = FeedParser.parse(jsonFeed)
        assertEquals("Pulso JSON", parsed.title)
        assertEquals(1, parsed.items.size)
        assertEquals("Redacción", parsed.items[0].author)
    }

    @Test
    fun opmlRoundTripPreservesCategoriesAndUrls() {
        val original = listOf(
            FeedEntity("f1", "Ciencia UE", "https://example.org/ciencia.xml", "Ciencia", null, 0L),
            FeedEntity("f2", "Tech Libre", "https://example.org/tech.xml", "Tecnología", null, 0L)
        )
        val xml = OpmlParser.exportOpml(original)
        val imported = OpmlParser.importOpml(xml)
        assertEquals(2, imported.size)
        assertEquals("Ciencia", imported.first { it.url == "https://example.org/ciencia.xml" }.category)
        assertEquals("Tecnología", imported.first { it.url == "https://example.org/tech.xml" }.category)
    }

    @Test
    fun imageDiskCacheEvictsOldestWhenOverLimit() {
        val cache = ImageDiskCache(tempFolder.newFolder("imgs"), maxSizeBytes = 100L)
        cache.put("https://example.org/1.jpg", ByteArray(60) { 1 })
        Thread.sleep(15)
        cache.put("https://example.org/2.jpg", ByteArray(60) { 2 })

        assertNull(cache.get("https://example.org/1.jpg"))
        assertNotNull(cache.get("https://example.org/2.jpg"))
        assertTrue(cache.sizeBytes() <= 100L)

        cache.clear()
        assertEquals(0L, cache.sizeBytes())
    }
}
