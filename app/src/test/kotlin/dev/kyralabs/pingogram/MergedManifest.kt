package dev.kyralabs.pingogram

import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

// The merged manifest of the variant under test, for the manifest tests of both flavors.

/** The merged manifest Robolectric runs, as the Android Gradle plugin's test config names it. */
internal fun mergedManifest(): Element {
    val config = Properties()
    Thread
        .currentThread()
        .contextClassLoader!!
        .getResourceAsStream("com/android/tools/test_config.properties")
        .use(config::load)
    val file = File(config.getProperty("android_merged_manifest"))
    return DocumentBuilderFactory
        .newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)
        .documentElement
}

internal fun Element.elements(tag: String): List<Element> {
    val nodes = getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as Element }
}

internal fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
