package com.google.idea.blaze.base.buildview

import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.AppendableTerminalDataStream
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.JediTerminal
import java.io.IOException
import javax.swing.JComponent

private val LOG = Logger.getInstance(PtyConsoleView::class.java)

private const val CLRF = "\r\n"
private const val ANSI_FG_RED = "\u001b[31m"
private const val ANSI_RESET = "\u001b[0m"

class PtyConsoleView(project: Project) : ExecutionConsole {

  companion object {
    val DEFAULT_SIZE = TermSize(80, 24)
  }

  private val stream = AppendableTerminalDataStream()
  private val terminal = Terminal(project, this, stream)

  val size: TermSize? get() = terminal.terminalPanel.terminalSizeFromComponent

  init {
    terminal.start(Connector(this))
  }

  override fun getComponent(): JComponent = terminal.component

  override fun getPreferredFocusableComponent(): JComponent? = component

  /**
   * Writes one or more lines to the terminal.
   * All linebreaks are adjusted for the pty terminal.
   */
  fun write(text: String) {
    // trim redundant whitespace at the end
    var adjustedText = text.trimEnd()
    // fix line separators for the pty terminal
    adjustedText = StringUtil.convertLineSeparators(adjustedText, CLRF)
    // add newline to terminate last line
    adjustedText = adjustedText + CLRF

    append(adjustedText)
  }

  /**
   * Like [write] but prints in red.
   */
  fun error(text: String) {
    write("$ANSI_FG_RED$text$ANSI_RESET")
  }

  /**
   * Appends to the raw terminal stream.
   * Allows for the usage of any ansi escape sequences.
   */
  fun append(text: String) {
    try {
      stream.append(text)
    } catch (e: IOException) {
      LOG.error("could not append to terminal stream", e)
    }
  }

  override fun dispose() = Unit
}

private class Terminal(
  project: Project,
  parent: Disposable,
  private val stream: AppendableTerminalDataStream,
) : JBTerminalWidget(project, JBTerminalSystemSettingsProviderBase(), parent) {

  override fun createTerminalStarter(terminal: JediTerminal, connector: TtyConnector): TerminalStarter? {
    return TerminalStarter(
      terminal,
      connector,
      stream,
      typeAheadManager,
      executorServiceManager,
    )
  }
}

private class Connector(parent: Disposable) : TtyConnector, Disposable {

  init {
    Disposer.register(parent, this)
  }

  private var connected = true

  override fun read(buf: CharArray, offset: Int, length: Int): Int = 0

  override fun write(bytes: ByteArray?) = Unit

  override fun write(string: String?) = Unit

  override fun isConnected(): Boolean =  connected

  override fun waitFor(): Int = 0

  override fun ready(): Boolean = true

  override fun getName(): String = "bazel-build-console"

  override fun close() = Unit

  // The terminal size for bazel is final. No use propagating resize events to the process.
  override fun resize(size: TermSize) = Unit

  override fun dispose() {
    connected = false
  }
}
