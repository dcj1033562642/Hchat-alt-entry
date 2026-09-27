# DexKit
-keep class org.luckypray.dexkit.DexKitBridge { *; }

# Miuix Nav/Compose resolves this composition local from library bytecode.
-keep class androidx.lifecycle.viewmodel.compose.** { *; }

# BeanShell 用户脚本引擎
-keep class bsh.** { *; }

# Android Xml API 返回系统 XmlPullParser 实现，接口 ABI 不能重命名或收窄
-keep class org.xmlpull.v1.** { *; }

-keep class h.Hchat.hooks.items.script.ScriptPluginRuntime { *; }
-keep class h.Hchat.hooks.items.script.ScriptPluginBridge { *; }
-keep class h.Hchat.hooks.items.script.ScriptFloatingGlassBarHandle { *; }
-keep class h.Hchat.hooks.items.script.ScriptDexKitBridge { *; }
-keep class h.Hchat.hooks.items.script.ScriptWaBridge { *; }
-keep class h.Hchat.hooks.items.script.ScriptAudioBridge { *; }
-keep class h.Hchat.hooks.items.script.ScriptMessageBean { *; }
-keep class h.Hchat.hooks.items.script.ScriptQuoteMsgBean { *; }
-keep class h.Hchat.hooks.items.script.ScriptPluginRuntime$SendResult { *; }
-keep class h.Hchat.hooks.items.protobuf.ProtobufPacketRuntime$Packet { *; }
-keep class h.Hchat.hooks.api.model.ContactLabelBean { *; }
-keep class h.Hchat.hooks.api.model.WeChatContact { *; }
-keep class h.Hchat.hooks.api.model.WeChatChatroom { *; }
-keep class h.Hchat.hooks.api.model.WeChatMessage { *; }
-keep class h.Hchat.hooks.api.model.WeChatQuoteMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatImageMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatVideoMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatFileMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatTransferMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatPatMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsMedia { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsPost { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsLivePhoto { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsPrepareResult { *; }
-keep class h.Hchat.hooks.api.core.WeChatApis { *; }
-keep class h.Hchat.hooks.api.runtime.WeChatDatabaseApi { *; }
-keep class h.Hchat.dexkit.DexBridgeHolder { *; }
-keep class h.Hchat.dexkit.DexFinder { *; }
-keep class h.Hchat.utils.KavaReflector { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.alibaba.fastjson2.** { *; }
-keep class me.hd.wauxv.plugin.api.callback.** { *; }
-keep class me.hd.wauxv.data.bean.** { *; }
-keep class me.hd.wauxv.data.bean.info.** { *; }
-keep class me.yun.silk.** { *; }
-keep class me.yun.silk.utils.** { *; }

# Xposed
-keep class de.robv.android.xposed.** { *; }
-keep class h.Hchat.ModuleEntry { *; }
-keep class h.Hchat.crash.NativeCrashBridge { *; }
-keep class h.Hchat.crash.CrashExitInfoApi30 { *; }

# Termux 终端：JNI 类的 native 方法按 Java_com_termux_terminal_JNI_* 符号绑定 libtermux.so，
# 类名/方法名一旦被 R8 改名，动态绑定就找不到符号。整包 keep 最稳。
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
-keepclasseswithmembernames class com.termux.terminal.JNI {
    native <methods>;
}
# 反射注入 native 库搜索路径用到的加载器
-keep class h.Hchat.loader.utils.NativeLibraryLoader { *; }
-keep class h.Hchat.hooks.items.script.agent.ProotTerminalView { *; }
-keep class h.Hchat.hooks.items.script.agent.ProotEnvironment { *; }

# 保留反射访问的微信内部类字段名
-keepclassmembers class * {
    java.lang.String e;
    java.lang.String f;
    java.lang.String h;
    java.lang.String i;
    java.lang.String m;
    java.lang.String d;
}
