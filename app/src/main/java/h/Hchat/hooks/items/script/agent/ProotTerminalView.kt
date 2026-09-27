package h.Hchat.hooks.items.script.agent

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import h.Hchat.utils.HLog

@SuppressLint("ViewConstructor")
// ☰ 会话菜单：新建会话 / 关闭会话（最后一个会话时即退出终端）/ 关闭终端（结束全部会话）
class ProotTerminalView(context: Context) : FrameLayout(context) {

    private val terminalView: TerminalView = TerminalView(ModuleResourceContext.of(context), null)
    private var session: TerminalSession? = null
    private var onFinished: ((Int) -> Unit)? = null

    var ctrlActive: Boolean = false
        private set
    var altActive: Boolean = false
        private set
    var shiftActive: Boolean = false
        private set

    fun setCtrlActive(active: Boolean) {
        ctrlActive = active
    }

    fun setAltActive(active: Boolean) {
        altActive = active
    }

    fun setShiftActive(active: Boolean) {
        shiftActive = active
    }

    fun setOnSessionFinished(cb: (Int) -> Unit) {
        onFinished = cb
    }

    fun start(): Boolean {
        runCatching {
            h.Hchat.loader.utils.NativeLibraryLoader()
                .loadTermux(context.applicationContext ?: context, javaClass.classLoader)
        }.onFailure { HLog.e("[Hchat:Term] libtermux 预加载失败: ${it.message}", it) }
        if (terminalView.parent == null) {
            val density = resources.displayMetrics.density
            var fontSize = Math.round(12f * density)
            if (fontSize % 2 == 1) fontSize--
            terminalView.setTextSize(fontSize)
            runCatching { terminalView.setTypeface(Typeface.MONOSPACE) }
            terminalView.isVerticalScrollBarEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val thumb = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0x66FFFFFF)
                    setSize(dpToPx(4f).toInt(), -1)
                }
                runCatching { terminalView.verticalScrollbarThumbDrawable = thumb }
            }
            terminalView.keepScreenOn = true
            addView(
                terminalView,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
        }
        val spec = ProotEnvironment.interactiveShell(context) ?: return false
        val newSession = TerminalSession(
            spec.executable,
            spec.cwd,
            spec.argv,
            spec.environment,
            2000,
            sessionClient,
        )
        session = newSession
        terminalView.attachSession(newSession)
        terminalView.setTerminalViewClient(viewClient)
        terminalView.onScreenUpdated()
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        terminalView.post { terminalView.updateSize() }
        terminalView.post {
            terminalView.requestFocus()
            showSoftKeyboard()
        }
        terminalView.postDelayed({ showSoftKeyboard() }, 350)
        TerminalBridge.attach(this)
        return true
    }

    fun paste(text: String) {
        val s = session ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        s.write(bytes, 0, bytes.size)
    }

    fun sendKey(sequence: String) = paste(sequence)

    fun clearScreen() = paste("clear\n")

    fun stopSelection() {
        runCatching { terminalView.stopTextSelectionMode() }
    }

    fun copyToClipboard(text: String?) {
        val value = text?.trim()
        if (value.isNullOrEmpty()) {
            toast("没有选中内容")
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm == null) {
            toast("复制失败：剪贴板不可用")
            return
        }
        runCatching { cm.setPrimaryClip(ClipData.newPlainText("Hchat 终端", value)) }
            .onSuccess { toast("已复制") }
            .onFailure {
                HLog.e("[Hchat:Term] 复制失败: ${it.message}", it)
                toast("复制失败")
            }
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = runCatching { cm?.primaryClip }.getOrNull()
        val text = if (clip != null && clip.itemCount > 0) {
            runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
        } else {
            null
        }
        if (text.isNullOrEmpty()) {
            toast("剪贴板是空的")
            return
        }
        paste(text)
        toast("已粘贴")
    }

    fun selectedText(): String? = runCatching {
        val field = terminalView.javaClass.getDeclaredField("mTextSelectionCursorController")
        field.isAccessible = true
        val controller = field.get(terminalView) ?: return null
        val sel = IntArray(4)
        controller.javaClass.getMethod("getSelectors", IntArray::class.java).invoke(controller, sel)
        terminalView.mEmulator?.getSelectedText(sel[0], sel[1], sel[2], sel[3])?.trim()
    }.getOrNull()

    fun showMoreMenu() {
        post {
            runCatching {
                AlertDialog.Builder(context)
                    .setTitle("终端")
                    .setItems(arrayOf("粘贴", "清屏", "发送 Ctrl+C")) { _, which ->
                        when (which) {
                            0 -> pasteFromClipboard()
                            1 -> clearScreen()
                            2 -> paste("\u0003")
                        }
                    }
                    .show()
            }.onFailure { HLog.e("[Hchat:Term] 更多菜单弹出失败: ${it.message}", it) }
        }
    }

    private fun toast(message: String) {
        post {
            runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }

    fun toggleCtrl(): Boolean {
        ctrlActive = !ctrlActive
        return ctrlActive
    }

    fun toggleAlt(): Boolean {
        altActive = !altActive
        return altActive
    }

    fun toggleSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager ?: return
        if (imm.isActive(terminalView)) {
            imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
        } else {
            showSoftKeyboard()
        }
    }

    fun requestTerminalFocus(): Boolean {
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        return terminalView.requestFocus()
    }

    fun showSoftKeyboard() {
        requestTerminalFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager ?: return
        imm.showSoftInput(terminalView, 0)
        terminalView.postDelayed({
            if (!imm.isActive(terminalView)) {
                runCatching {
                    imm.toggleSoftInput(
                        android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0
                    )
                }
            }
        }, 250)
    }

    fun isSessionRunning(): Boolean = session?.isRunning == true

    fun destroySession() {
        runCatching { session?.finishIfRunning() }
        session = null
        TerminalBridge.detach(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        TerminalBridge.detach(this)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            if (changedSession == session) terminalView.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {}

        override fun onSessionFinished(finishedSession: TerminalSession) {
            val code = runCatching { finishedSession.exitStatus }.getOrDefault(-1)
            onFinished?.invoke(code)
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            copyToClipboard(text)
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            pasteFromClipboard()
        }
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(tag: String?, message: String?) {
            HLog.e("[Hchat:Term] $tag $message")
        }

        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            HLog.e("[Hchat:Term] $tag $message", e)
        }

        override fun logStackTrace(tag: String?, e: Exception?) {
            HLog.e("[Hchat:Term] $tag", e)
        }
    }

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float = scale

        override fun onSingleTapUp(e: MotionEvent?) {
            showSoftKeyboard()
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = true
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
        override fun onLongPress(event: MotionEvent?): Boolean = false

        override fun readControlKey(): Boolean = ctrlActive
        override fun readAltKey(): Boolean = altActive
        override fun readShiftKey(): Boolean = shiftActive
        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
        override fun onEmulatorSet() {
            terminalView.onScreenUpdated()
        }

        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }
}

object TerminalBridge {

    @Volatile
    private var ref: java.lang.ref.WeakReference<ProotTerminalView>? = null

    fun attach(view: ProotTerminalView) {
        ref = java.lang.ref.WeakReference(view)
    }

    fun detach(view: ProotTerminalView) {
        if (ref?.get() === view) ref = null
    }

    fun current(): ProotTerminalView? = ref?.get()
}
