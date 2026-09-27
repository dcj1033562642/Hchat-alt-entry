package h.Hchat.hooks.items.script.agent

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import h.Hchat.utils.HLog

interface TermuxSessionView {
    fun onTerminalTextChanged(session: TerminalSession)
    fun onTerminalSessionFinished(session: TerminalSession)
    fun onTerminalCopyRequested(text: String?)
    fun onTerminalPasteRequested()
}

class TermuxSession(val id: Int, val session: TerminalSession) {

    val title: String get() = "会话 $id"

    val isRunning: Boolean get() = runCatching { session.isRunning }.getOrDefault(false)
}

// 终端会话池：退出页面只解绑视图，关闭终端才结束全部会话（dexclub 等常驻服务保留）
object TermuxSessions {

    private val items = ArrayList<TermuxSession>()

    @Volatile
    private var currentId = -1

    @Volatile
    private var attachedView: TermuxSessionView? = null

    private var nextId = 1

    fun list(): List<TermuxSession> = synchronized(items) { items.toList() }

    fun current(): TermuxSession? = synchronized(items) { items.firstOrNull { it.id == currentId } }

    fun attachView(view: TermuxSessionView?) {
        attachedView = view
    }

    fun create(context: Context): TermuxSession? {
        val spec = runCatching { ProotEnvironment.interactiveShell(context) }.getOrNull() ?: return null
        val holder = TermuxSession(
            nextId++,
            TerminalSession(
                spec.executable,
                spec.cwd,
                spec.argv,
                spec.environment,
                2000,
                client,
            ),
        )
        synchronized(items) {
            items.removeAll { !it.isRunning }
            items.add(holder)
        }
        currentId = holder.id
        return holder
    }

    fun ensureCurrent(context: Context): TermuxSession? {
        val cur = current()
        if (cur != null && cur.isRunning) return cur
        return create(context)
    }

    fun select(id: Int): TermuxSession? {
        val target = synchronized(items) { items.firstOrNull { it.id == id } } ?: return null
        currentId = id
        return target
    }

    fun close(id: Int) {
        val target = synchronized(items) {
            val found = items.firstOrNull { it.id == id }
            if (found != null) items.remove(found)
            found
        } ?: return
        if (currentId == id) {
            currentId = synchronized(items) { items.lastOrNull()?.id ?: -1 }
        }
        runCatching { target.session.finishIfRunning() }
    }

    fun closeSessions() {
        val all = synchronized(items) {
            val snapshot = items.toList()
            items.clear()
            snapshot
        }
        currentId = -1
        all.forEach { runCatching { it.session.finishIfRunning() } }
    }

    fun closeAll(context: Context) {
        val all = synchronized(items) {
            val snapshot = items.toList()
            items.clear()
            snapshot
        }
        currentId = -1
        all.forEach { runCatching { it.session.finishIfRunning() } }
        if (all.isEmpty()) return
        Thread { runCatching { ProotEnvironment.stopDexclub(context) } }
            .apply { isDaemon = true }
            .start()
    }

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            attachedView?.onTerminalTextChanged(changedSession)
        }

        override fun onTitleChanged(changedSession: TerminalSession) {}

        override fun onSessionFinished(finishedSession: TerminalSession) {
            attachedView?.onTerminalSessionFinished(finishedSession)
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            attachedView?.onTerminalCopyRequested(text)
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            attachedView?.onTerminalPasteRequested()
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
}
