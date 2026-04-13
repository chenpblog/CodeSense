package com.deeptek.ai.idea.analysis

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

/**
 * 入口点检测器
 *
 * 判断一个 PsiMethod 是否是系统入口（HTTP API / 定时任务 / MQ / Dubbo / 事件监听等），
 * 并提取入口的详细信息（URL 路径、cron 表达式、topic 等）。
 */
object EntryPointDetector {

    // ====== 方法级别入口注解 ======
    private val METHOD_ENTRY_ANNOTATIONS = setOf(
        // Spring MVC
        "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
        // 定时任务
        "Scheduled",
        // MQ
        "RocketMQMessageListener", "KafkaListener",
        // 事件
        "EventListener", "TransactionalEventListener",
        // 初始化生命周期
        "PostConstruct"
    )

    // ====== Dubbo 类级别注解 ======
    private val DUBBO_CLASS_ANNOTATIONS = setOf(
        "DubboService",
        "org.apache.dubbo.config.annotation.Service",
        "com.alibaba.dubbo.config.annotation.Service",
        "org.apache.dubbo.config.annotation.DubboService"
    )

    /**
     * 判断方法是否是入口点
     */
    fun isEntryPoint(psiMethod: PsiMethod): Boolean {
        // 1. 检查方法级别注解
        if (hasMethodEntryAnnotation(psiMethod)) return true

        // 2. 检查 Dubbo 类级别注解
        if (isDubboEntryMethod(psiMethod)) return true

        return false
    }

    /**
     * 提取入口点详细信息
     */
    fun extractEntryPointInfo(psiMethod: PsiMethod, methodInfo: MethodInfo): EntryPointInfo? {
        // 检查方法注解
        for (anno in psiMethod.annotations) {
            val name = anno.qualifiedName ?: continue

            when {
                // === Spring MVC HTTP ===
                name.endsWith("GetMapping") || name.endsWith("PostMapping") ||
                name.endsWith("PutMapping") || name.endsWith("DeleteMapping") ||
                name.endsWith("PatchMapping") || name.endsWith("RequestMapping") -> {
                    val httpMethod = when {
                        name.endsWith("GetMapping") -> "GET"
                        name.endsWith("PostMapping") -> "POST"
                        name.endsWith("PutMapping") -> "PUT"
                        name.endsWith("DeleteMapping") -> "DELETE"
                        name.endsWith("PatchMapping") -> "PATCH"
                        else -> extractHttpMethod(anno) ?: "REQUEST"
                    }
                    val path = extractAnnotationValue(anno, "value")
                        ?: extractAnnotationValue(anno, "path") ?: ""
                    val classPath = extractClassLevelPath(psiMethod)
                    return EntryPointInfo(
                        method = methodInfo,
                        type = EntryPointType.HTTP_API,
                        path = "$httpMethod $classPath$path",
                        triggerCondition = null
                    )
                }

                // === 定时任务 ===
                name.endsWith("Scheduled") -> {
                    val cron = extractAnnotationValue(anno, "cron")
                    val fixedRate = extractAnnotationValue(anno, "fixedRate")
                    val fixedDelay = extractAnnotationValue(anno, "fixedDelay")
                    val trigger = cron?.let { "cron=$it" }
                        ?: fixedRate?.let { "fixedRate=${it}ms" }
                        ?: fixedDelay?.let { "fixedDelay=${it}ms" }
                    return EntryPointInfo(
                        method = methodInfo,
                        type = EntryPointType.SCHEDULED,
                        path = null,
                        triggerCondition = trigger
                    )
                }

                // === RocketMQ ===
                name.endsWith("RocketMQMessageListener") -> {
                    val topic = extractAnnotationValue(anno, "topic")
                    val group = extractAnnotationValue(anno, "consumerGroup")
                    return EntryPointInfo(
                        method = methodInfo,
                        type = EntryPointType.MQ_LISTENER,
                        path = null,
                        triggerCondition = "topic=$topic, group=$group"
                    )
                }

                // === Kafka ===
                name.endsWith("KafkaListener") -> {
                    val topics = extractAnnotationValue(anno, "topics")
                    return EntryPointInfo(
                        method = methodInfo,
                        type = EntryPointType.MQ_LISTENER,
                        path = null,
                        triggerCondition = "topics=$topics"
                    )
                }

                // === 事件监听 ===
                name.endsWith("EventListener") || name.endsWith("TransactionalEventListener") -> {
                    return EntryPointInfo(
                        method = methodInfo,
                        type = EntryPointType.EVENT_LISTENER,
                        path = null,
                        triggerCondition = "EventListener"
                    )
                }
            }
        }

        // === Dubbo RPC ===
        if (isDubboEntryMethod(psiMethod)) {
            val containingClass = psiMethod.containingClass ?: return null
            val interfaceName = containingClass.interfaces
                .firstOrNull { it.findMethodsByName(psiMethod.name, false).isNotEmpty() }
                ?.qualifiedName?.substringAfterLast('.') ?: "UnknownFacade"
            return EntryPointInfo(
                method = methodInfo,
                type = EntryPointType.DUBBO_RPC,
                path = "$interfaceName.${psiMethod.name}",
                triggerCondition = null
            )
        }

        return null
    }

    // ====== 私有辅助方法 ======

    private fun hasMethodEntryAnnotation(psiMethod: PsiMethod): Boolean {
        return psiMethod.annotations.any { anno ->
            val qn = anno.qualifiedName ?: ""
            METHOD_ENTRY_ANNOTATIONS.any { qn.endsWith(it) }
        }
    }

    private fun isDubboEntryMethod(psiMethod: PsiMethod): Boolean {
        val containingClass = psiMethod.containingClass ?: return false
        val hasDubboAnno = containingClass.annotations.any { anno ->
            val qn = anno.qualifiedName ?: ""
            DUBBO_CLASS_ANNOTATIONS.any { qn.endsWith(it) || qn == it }
        }
        return hasDubboAnno
                && psiMethod.hasModifierProperty(PsiModifier.PUBLIC)
                && isInterfaceMethod(psiMethod)
    }

    private fun isInterfaceMethod(psiMethod: PsiMethod): Boolean {
        val containingClass = psiMethod.containingClass ?: return false
        return containingClass.interfaces.any { iface ->
            iface.findMethodsByName(psiMethod.name, false).isNotEmpty()
        }
    }

    private fun extractAnnotationValue(anno: com.intellij.psi.PsiAnnotation, attrName: String): String? {
        return anno.findAttributeValue(attrName)?.text
            ?.trim('"', '{', '}', ' ')
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun extractHttpMethod(anno: com.intellij.psi.PsiAnnotation): String? {
        val methodAttr = anno.findAttributeValue("method")?.text ?: return null
        return when {
            methodAttr.contains("GET") -> "GET"
            methodAttr.contains("POST") -> "POST"
            methodAttr.contains("PUT") -> "PUT"
            methodAttr.contains("DELETE") -> "DELETE"
            methodAttr.contains("PATCH") -> "PATCH"
            else -> null
        }
    }

    private fun extractClassLevelPath(psiMethod: PsiMethod): String {
        val cls = psiMethod.containingClass ?: return ""
        for (anno in cls.annotations) {
            if (anno.qualifiedName?.endsWith("RequestMapping") == true) {
                return extractAnnotationValue(anno, "value")
                    ?: extractAnnotationValue(anno, "path") ?: ""
            }
        }
        return ""
    }
}
