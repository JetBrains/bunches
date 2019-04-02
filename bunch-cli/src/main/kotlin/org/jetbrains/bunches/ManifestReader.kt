package org.jetbrains.bunches

import java.net.URL
import java.util.jar.Manifest

object ManifestReader {
    fun readAttribute(name: String): String? {
        val clazz = this::class.java
        val className = clazz.simpleName + ".class"
        val classPath = clazz.getResource(className).toString()
        if (!classPath.startsWith("jar")) {
            return null
        }

        val manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF"

        URL(manifestPath).openStream().use { openStream ->
            return Manifest(openStream).mainAttributes.getValue(name)
        }
    }
}