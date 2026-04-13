package com.deeptek.ai.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * CodeSense AI 全局配置持久化服务
 *
 * 存储模型配置、功能开关等全局设置。
 * 通过 IDEA 的 PersistentStateComponent 机制自动持久化到 XML 文件。
 *
 * 使用方式:
 * ```kotlin
 * val settings = CodeSenseSettings.getInstance()
 * val provider = settings.getDefaultProvider()
 * ```
 */
@State(
    name = "CodeSenseSettings",
    storages = [Storage("codesense-ai.xml")]
)
class CodeSenseSettings : PersistentStateComponent<CodeSenseSettings.State> {

    /**
     * 持久化状态数据
     */
    data class State(
        /** 默认使用的模型 ID */
        var defaultProviderId: String = "minimax-default",

        /** 已配置的模型提供者列表 */
        var providers: MutableList<ProviderConfig> = mutableListOf(
            // 预置 MiniMax 默认配置
            ProviderConfig(
                id = "minimax-default",
                displayName = "MiniMax",
                type = ProviderType.MINIMAX,
                baseUrl = ProviderType.MINIMAX.defaultBaseUrl,
                modelName = ProviderType.MINIMAX.defaultModel,
                apiKey = ""
            ),
            // 预置 GLM 默认配置
            ProviderConfig(
                id = "glm-default",
                displayName = "GLM (智谱)",
                type = ProviderType.GLM,
                baseUrl = ProviderType.GLM.defaultBaseUrl,
                modelName = ProviderType.GLM.defaultModel,
                apiKey = ""
            )
        ),

        // ====== 功能开关 ======
        var enableCodeReview: Boolean = true,
        var enableImpactAnalysis: Boolean = true,
        var enableAiRiskAssessment: Boolean = true,

        // ====== 参数配置 ======
        var maxReviewFiles: Int = 20,
        var maxCallHierarchyDepth: Int = 10
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    // ====== 便捷方法 ======

    /**
     * 获取默认的模型提供者配置
     *
     * @throws IllegalStateException 如果没有配置任何模型
     */
    fun getDefaultProvider(): ProviderConfig {
        return myState.providers.find { it.id == myState.defaultProviderId }
            ?: myState.providers.firstOrNull()
            ?: throw IllegalStateException("请先在 Settings → Tools → CodeSense AI 中配置至少一个模型提供者")
    }

    /**
     * 获取所有模型提供者的显示名称列表（用于下拉框）
     */
    fun getProviderDisplayNames(): List<String> {
        return myState.providers.map { it.displayName }
    }

    /**
     * 根据显示名称查找提供者
     */
    fun findProviderByName(name: String): ProviderConfig? {
        return myState.providers.find { it.displayName == name }
    }

    /**
     * 根据 ID 查找提供者
     */
    fun findProviderById(id: String): ProviderConfig? {
        return myState.providers.find { it.id == id }
    }

    /**
     * 添加一个新的提供者配置
     */
    fun addProvider(config: ProviderConfig) {
        myState.providers.add(config)
    }

    /**
     * 删除指定 ID 的提供者
     */
    fun removeProvider(id: String) {
        myState.providers.removeIf { it.id == id }
        // 如果删除的是默认模型，切换到第一个
        if (myState.defaultProviderId == id) {
            myState.defaultProviderId = myState.providers.firstOrNull()?.id ?: ""
        }
    }

    /**
     * 设置默认模型
     */
    fun setDefaultProvider(id: String) {
        if (myState.providers.any { it.id == id }) {
            myState.defaultProviderId = id
        }
    }

    /**
     * 检查默认模型的 API Key 是否已配置
     */
    fun isDefaultProviderConfigured(): Boolean {
        return try {
            getDefaultProvider().apiKey.isNotBlank()
        } catch (e: IllegalStateException) {
            false
        }
    }

    companion object {
        fun getInstance(): CodeSenseSettings {
            return ApplicationManager.getApplication().getService(CodeSenseSettings::class.java)
        }
    }
}
