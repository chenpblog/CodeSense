package com.deeptek.ai.idea.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.*
import java.util.UUID
import javax.swing.JComponent

/**
 * 模型提供者配置对话框
 *
 * 用于添加新的模型提供者或编辑已有的配置。
 * 选择提供者类型后会自动填充默认的 Base URL 和模型名称。
 *
 * @param existingConfig 如果不为 null，则为编辑模式；否则为新增模式
 */
class ProviderConfigDialog(
    private val existingConfig: ProviderConfig?
) : DialogWrapper(true) {

    // 编辑中的配置副本
    private val config = existingConfig?.copy() ?: ProviderConfig(
        id = UUID.randomUUID().toString(),
        type = ProviderType.MINIMAX,
        displayName = ProviderType.MINIMAX.displayName,
        baseUrl = ProviderType.MINIMAX.defaultBaseUrl,
        modelName = ProviderType.MINIMAX.defaultModel
    )

    // UI 绑定字段
    private var selectedType: ProviderType = config.type
    private var displayName: String = config.displayName
    private var baseUrl: String = config.baseUrl
    private var apiKey: String = config.apiKey
    private var modelName: String = config.modelName
    private var maxTokens: Int = config.maxTokens
    private var temperature: Double = config.temperature
    private var supportToolCalling: Boolean = config.supportToolCalling
    private var supportStreaming: Boolean = config.supportStreaming

    init {
        title = if (existingConfig != null) "编辑模型提供者" else "添加模型提供者"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("提供者类型:") {
            comboBox(ProviderType.entries.map { it.displayName })
                .applyToComponent {
                    selectedItem = selectedType.displayName
                    addActionListener {
                        val selected = selectedItem as? String ?: return@addActionListener
                        val type = ProviderType.fromDisplayName(selected)
                        selectedType = type
                        // 如果是新增模式，自动填充默认值
                        if (existingConfig == null || existingConfig.type != type) {
                            displayName = type.displayName
                            baseUrl = type.defaultBaseUrl
                            modelName = type.defaultModel
                        }
                    }
                }
                .comment("可选: MiniMax / GLM / DeepSeek / 通义千问 / 自定义")
        }

        row("显示名称:") {
            textField()
                .columns(30)
                .bindText(::displayName)
        }
        row("Base URL:") {
            textField()
                .columns(40)
                .bindText(::baseUrl)
                .comment("API 的完整请求地址 (Anthropic Messages API 格式)")
        }
        row("API Key:") {
            passwordField()
                .columns(40)
                .bindText(::apiKey)
        }
        row("模型名称:") {
            textField()
                .columns(30)
                .bindText(::modelName)
        }

        collapsibleGroup("高级选项") {
            row("最大 Token:") {
                spinner(256..128000, 256)
                    .bindIntValue(::maxTokens)
            }
            row("Temperature:") {
                textField()
                    .columns(10)
                    .bindText(
                        getter = { temperature.toString() },
                        setter = { temperature = it.toDoubleOrNull() ?: 0.7 }
                    )
                    .comment("0.0 - 2.0, 默认 0.7")
            }
            row {
                checkBox("支持 Tool Calling")
                    .bindSelected(::supportToolCalling)
            }
            row {
                checkBox("支持流式输出")
                    .bindSelected(::supportStreaming)
            }
        }
    }

    /**
     * 获取配置结果
     *
     * 在 dialog.showAndGet() 返回 true 后调用此方法获取用户填写的配置。
     */
    fun getProviderConfig(): ProviderConfig {
        return ProviderConfig(
            id = config.id,
            displayName = displayName,
            type = selectedType,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            maxTokens = maxTokens,
            temperature = temperature,
            supportToolCalling = supportToolCalling,
            supportStreaming = supportStreaming
        )
    }
}
