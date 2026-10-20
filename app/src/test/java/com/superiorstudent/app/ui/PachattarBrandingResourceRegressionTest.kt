package com.superiorstudent.app.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Source-resource regression checks for the More menu's Pachattar% brand header.
 *
 * These tests intentionally validate the XML assets and theme bindings without claiming
 * to replace a rendered, on-device UI test.
 */
class PachattarBrandingResourceRegressionTest {

    @Test
    fun moreMenuUsesFortyDpMulticolourBrandMark() {
        val layout = parseXml("app/src/main/res/layout/activity_main.xml")
        val logo = elementById(layout, "ImageView", "moreBrandLogo")

        assertEquals("40dp", logo.getAttribute("android:layout_width"))
        assertEquals("40dp", logo.getAttribute("android:layout_height"))
        assertEquals("@drawable/ic_pachattar_mark", logo.getAttribute("android:src"))

        val mark = parseXml("app/src/main/res/drawable/ic_pachattar_mark.xml")
        val paths = mark.getElementsByTagName("path")
        val fillColours = (0 until paths.length).mapNotNull { index ->
            (paths.item(index) as? Element)?.getAttribute("android:fillColor")
        }.toSet()

        assertTrue("The mark must retain the lime rounded-square fill.", fillColours.contains("#D4F34A"))
        assertTrue("The mark must retain the dark-green P fill.", fillColours.contains("#0B3D2E"))
        assertTrue("The mark must retain the coral dot.", fillColours.contains("#FF6B4A"))
    }

    @Test
    fun wordmarkAndPercentKeepTheirTextAndThemeAwareColours() {
        val layout = parseXml("app/src/main/res/layout/activity_main.xml")
        val title = elementById(layout, "TextView", "moreTitle")
        val percent = elementById(layout, "TextView", "moreTitlePercent")

        assertEquals("Pachattar", title.getAttribute("android:text"))
        assertEquals("%", percent.getAttribute("android:text"))
        assertEquals("#D4F34A", percent.getAttribute("android:textColor"))

        val mainActivity = File(projectRoot(), "app/src/main/java/com/superiorstudent/app/MainActivity.kt")
            .readText(Charsets.UTF_8)
        assertTrue(
            "The wordmark should follow the active theme.",
            mainActivity.contains("binding.moreTitle.setTextColor(Palette.ink(this))")
        )
        assertTrue(
            "The percent should use the active theme's lime token.",
            mainActivity.contains("binding.moreTitlePercent.setTextColor(Palette.lime(this))")
        )
        assertTrue(
            "The multicolour brand mark must not be tinted by generic icon styling.",
            mainActivity.contains("view.imageTintList = if (view.id == R.id.moreBrandLogo)")
        )
    }

    private fun parseXml(relativePath: String): Document {
        val file = File(projectRoot(), relativePath)
        assertTrue("Expected resource file to exist: ${file.path}", file.isFile)
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .apply { documentElement.normalize() }
    }

    private fun elementById(document: Document, tagName: String, id: String): Element {
        val nodes = document.getElementsByTagName(tagName)
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            if (element.getAttribute("android:id") == "@+id/$id") return element
        }
        throw AssertionError("Could not find <$tagName> with id @+id/$id")
    }

    private fun projectRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).canonicalFile
        while (current != null) {
            if (File(current, "app/src/main/res/layout/activity_main.xml").isFile) {
                return current
            }
            if (current.name == "app" &&
                File(current, "src/main/res/layout/activity_main.xml").isFile
            ) {
                return current.parentFile ?: current
            }
            current = current.parentFile
        }
        throw AssertionError("Could not locate the repository root from user.dir")
    }
}
