// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.bunches.idea.highlighting

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.bunches.idea.util.BunchFileUtils

class BunchLanguageSubstitutor : LanguageSubstitutor() {
    override fun getLanguage(file: VirtualFile, project: Project): Language? {
        return substituteLanguage(file, project)
    }

    companion object {
        fun substituteLanguage(file: VirtualFile, project: Project): Language? {
            val bunchExtensions = BunchFileUtils.bunchExtensions(project) ?: return null

            val name = file.name
            val extension = name.substringAfterLast(".", "")
            if (extension !in bunchExtensions) {
                return null
            }

            val nameWithoutExtension = name.substringBeforeLast(".")
            val secondExtension = nameWithoutExtension.substringAfterLast(".", "")
            if (secondExtension.isEmpty()) {
                return null
            }

            if (secondExtension.toLowerCase() == "kt") {
                // Temporary disable till problem with Kotlin resolve is fixed
                return null
            }

            val actualFileType = FileTypeManager.getInstance().getFileTypeByExtension(secondExtension)
            if (actualFileType is LanguageFileType) {
                return actualFileType.language
            }

            return null
        }
    }
}
