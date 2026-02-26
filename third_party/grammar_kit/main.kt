package com.google.idea.grammar_kit

import com.intellij.lang.ASTFactory
import com.intellij.lang.LanguageASTFactory
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType
import org.intellij.grammar.BnfLanguage
import org.intellij.grammar.BnfParserDefinition
import org.intellij.grammar.LightPsi
import org.intellij.grammar.generator.ParserGenerator
import org.intellij.grammar.psi.BnfFile
import org.intellij.grammar.psi.BnfTypes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main(args: Array<String>) {
  require(args.size == 2) { "Usage: <input bnf file> <output source jar>" }

  val sources = generateSources(args[0])
  generateSourceJar(sources, Path.of(args[1]))
}

private fun generateSources(inputFile: String): Path {
  val inputPath = Path.of(inputFile)
  val sourcePath = Path.of("sources")

  LightPsi.init()
  LightPsi.Init.addKeyedExtension(LanguageASTFactory.INSTANCE, BnfLanguage.INSTANCE, BnfASTFactory(), null)

  val psiFile = LightPsi.parseFile(inputPath.toFile(), BnfParserDefinition())
  ParserGenerator(psiFile as BnfFile, inputPath.parent.toString(), sourcePath.toString(), "").generate()

  return sourcePath
}

private fun generateSourceJar(sources: Path, srcjar: Path) {
  Files.createDirectories(srcjar.parent)

  ZipOutputStream(Files.newOutputStream(srcjar, StandardOpenOption.CREATE)).use { zip ->
    Files.walk(sources).filter { Files.isRegularFile(it) }.forEach { file ->
      zip.putNextEntry(ZipEntry(sources.relativize(file).toString()))
      Files.newInputStream(file).use { it.transferTo(zip) }
      zip.closeEntry()
    }
  }
}

private class BnfASTFactory : ASTFactory() {
  override fun createComposite(type: IElementType): CompositeElement {
    return BnfTypes.Factory.createElement(type)
  }
}
