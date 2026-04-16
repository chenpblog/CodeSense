# 执行计划：将 JsonBeanPreviewDialog 的错误提示改为 Toast 模式

## 问题描述
在 `JsonBeanPreviewDialog` 中，当未配置 AI 时，点击 "AI 转换" 会抛出异常（例如缺少模型配置），但当前只使用了 `setErrorText` 来展示错误信息。由于我们屏蔽了默认的底部操作栏 (`createActions() = emptyArray()`)，导致底部的 `setErrorText` 可能无法正常显示，用户感知为“没有提示”。

## 解决方案
废弃 `setErrorText`，改为使用 `JBPopupFactory` 创建 Toast 风格的 `Balloon` 进行提示。对发生错误进行集中改造。

### 改造范围：
`src/main/kotlin/com/deeptek/ai/idea/ui/json2bean/JsonBeanPreviewDialog.kt`

具体替换以下几处 `setErrorText` 逻辑为 Toast 提示 (类型为 `MessageType.ERROR`)：
1. `doAiTranslation(btn: JButton)` 的 `catch` 块中关于 AI 转换错误的提示。
2. `doRegenerateBean(btn: JButton)` 校验英文 JSON 是否合法时的提示。
3. `doRegenerateBean(btn: JButton)` 的 `catch` 块中关于生成代码错误的提示。
4. `generateFile()` 的 `catch` 块中关于写入文件的错误提示。

### 实现细节举例
```kotlin
com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder("AI 转换发生错误: ${e.message}", com.intellij.openapi.ui.MessageType.ERROR, null)
    .setFadeoutTime(3000)
    .createBalloon()
    .show(com.intellij.ui.awt.RelativePoint.getCenterOf(btn), com.intellij.openapi.ui.popup.Balloon.Position.above)
```
其中 `generateFile()` 没有传入 `btn`，可以考虑将对应按钮传入并在按钮上方弹出，或者在当前弹窗中心弹出。

## 验证
触发以上几种异常场景，验证是否能正确弹出红色/错误样式的 Toast 通知。
