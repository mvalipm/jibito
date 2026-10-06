package ir.jibito.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {

    private val sample = """
        # تغییرات جیبیتو

        توضیح بالای فایل نادیده گرفته می‌شود.
        - این خط هم (قبل از اولین نسخه)

        ## 0.52.0
        - صفحه‌ی تازه
        - خط دوم

        ## 0.51.0
        - حساب‌ها

        ## 0.50.9
    """.trimIndent()

    @Test
    fun parsesSectionsAndNotes() {
        val releases = Changelog.parse(sample)
        assertEquals(listOf("0.52.0", "0.51.0"), releases.map { it.version })
        assertEquals(listOf("صفحه‌ی تازه", "خط دوم"), releases[0].notes)
        assertEquals(listOf("حساب‌ها"), releases[1].notes)
    }

    @Test
    fun comparesVersionsNumericallyAndIgnoresBuildSuffix() {
        assertTrue(Changelog.compare("0.52.0", "0.51.10") > 0)
        assertTrue(Changelog.compare("0.9.0", "0.10.0") < 0)
        assertEquals(0, Changelog.compare("0.52.0-165", "0.52.0"))
        assertEquals(0, Changelog.compare("1.0", "1.0.0"))
    }

    @Test
    fun newSinceShowsEverythingAfterLastSeenUpToCurrent() {
        val releases = Changelog.parse(
            """
            ## 0.54.0
            - آینده
            ## 0.53.0
            - سه
            ## 0.52.0
            - دو
            ## 0.51.0
            - یک
            """.trimIndent()
        )
        assertEquals(listOf("0.53.0", "0.52.0"), Changelog.newSince(releases, "0.51.0", "0.53.0").map { it.version })
        assertEquals(emptyList<String>(), Changelog.newSince(releases, "0.53.0", "0.53.0").map { it.version })
        // از نسخه‌ای آمده که این صفحه را نداشت ← فقط همین نسخه
        assertEquals(listOf("0.53.0"), Changelog.newSince(releases, null, "0.53.0").map { it.version })
    }

    /** CHANGELOG.md واقعی: بخش بالایی همان versionName است، هر بخش خط دارد و نسخه‌ها رو به پایین کم می‌شوند */
    @Test
    fun repositoryChangelogMatchesVersionName() {
        val path = requireNotNull(System.getProperty("jibito.changelog")) { "Gradle باید مسیر CHANGELOG.md را بدهد" }
        val versionName = System.getProperty("jibito.versionName").orEmpty()
        val text = File(path).readText()
        val releases = Changelog.parse(text)
        assertTrue("CHANGELOG.md بخشی ندارد", releases.isNotEmpty())
        assertEquals(
            "versionName عوض شده ولی CHANGELOG.md بخش «## $versionName» ندارد (یا بالاترین بخش نیست)",
            versionName,
            releases.first().version,
        )
        val headings = Regex("^## ", RegexOption.MULTILINE).findAll(text).count()
        assertEquals("هر بخش «## نسخه» دست‌کم یک خطِ «- » لازم دارد", headings, releases.size)
        releases.zipWithNext().forEach { (newer, older) ->
            assertTrue("${newer.version} باید بالاتر از ${older.version} باشد", Changelog.compare(newer.version, older.version) > 0)
        }
    }
}
