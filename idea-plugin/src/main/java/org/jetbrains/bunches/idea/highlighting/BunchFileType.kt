// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.bunches.idea.highlighting

import com.intellij.openapi.fileTypes.InternalFileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bunches.idea.util.BunchFileUtils
import javax.swing.Icon

class BunchFileType :
    LanguageFileType(PlainTextLanguage.INSTANCE),
    FileTypeIdentifiableByVirtualFile,
    InternalFileType {

    override fun isMyFileType(file: VirtualFile): Boolean {
        val extension = file.extension

        for (project in ProjectManager.getInstance().openProjects) {
            val bunchExtensions = BunchFileUtils.bunchExtensions(project) ?: continue
            if (extension in bunchExtensions) {
                return true
            }
        }

        return false
    }

    override fun getName(): String = "Bunch"
    override fun getDescription(): String = "Bunch"
    override fun getDefaultExtension(): String = ""
    override fun getIcon(): Icon? = PlainTextFileType.INSTANCE.icon
    override fun isReadOnly(): Boolean = true
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    companion object {
        val INSTANCE: LanguageFileType = BunchFileType()
    }
}