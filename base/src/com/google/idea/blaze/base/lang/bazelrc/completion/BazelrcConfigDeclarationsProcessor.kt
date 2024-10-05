package com.google.idea.blaze.base.lang.bazelrc.completion

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcElement
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcFile
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcImport
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcLine

class BazelrcConfigDeclarationsProcessor(private val inputElement: BazelrcLine) : Processor<BazelrcElement> {
  var seenFiles = mutableSetOf<VirtualFile>()
  var results = mutableMapOf<String, BazelrcFile>()

  override fun process(currentElement: BazelrcElement): Boolean {
    if (currentElement == inputElement) return true
    processElement(currentElement)
    return true
  }

  fun processFile(file: BazelrcFile?) {
    file?.virtualFile?.apply {
      if (seenFiles.add(this)) {
        file.imports.map(::processElement)
        file.lines.map(::processElement)
      }
    }
  }

  fun processElement(current: BazelrcElement) =
    when (current) {
      is BazelrcLine -> processLine(current)
      is BazelrcImport -> processImport(current)
      else -> {}
    }

  private fun processLine(line: BazelrcLine) {
    (line.containingFile as? BazelrcFile)?.apply {
      line.configName()?.let { results[it] = this }
    }
  }

  private fun processImport(element: BazelrcImport) {
    processFile(element.reference?.resolve() as? BazelrcFile)
  }
}
