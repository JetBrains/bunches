// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.bunches.idea.highlighting

import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

/**
 * Component monitors bunch file editor open and disables analyses in them by default.
 */
class BunchProjectComponent(private val myProject: Project) : EditorFactoryListener {
    init {
        EditorFactory.getInstance().addEditorFactoryListener(this, myProject)
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (editor !is EditorImpl) {
            return
        }

        val document = editor.document
        val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return

        val virtualFile = file.virtualFile ?: return

        val isBunchFile = BunchLanguageSubstitutor.substituteLanguage(virtualFile, myProject) != null
        if (!isBunchFile) return

        HighlightLevelUtil.forceRootHighlighting(file, FileHighlightingSetting.SKIP_HIGHLIGHTING)
    }
}
