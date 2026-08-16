package com.sentinel.app.data

/**
 * 科目配置 - 定义各科目的专属关键词和提示词
 */
enum class SubjectConfig(
    val displayName: String,
    val keywords: List<String>,
    val questionPatterns: List<String>,
    val aiSystemPrompt: String,
    val icon: String // Emoji 用于UI展示
) {
    // 通用模式
    GENERAL(
        displayName = "通用模式",
        keywords = listOf(
            "问题", "提问", "回答", "请回答", "谁来回答", "请说", "思考",
            "为什么", "怎么", "如何", "是什么", "请分析", "解释一下",
            "讨论", "谈谈", "你说", "你的看法", "有什么想法"
        ),
        questionPatterns = listOf(
            "请.*回答", "谁来.*回答", ".*是个问题", ".*问题来了",
            "有没有.*问题", ".*你怎么看", ".*是什么", ".*为什么",
            ".*如何.*", "解释.*", "分析.*", "讨论.*"
        ),
        aiSystemPrompt = """你是一个智能学习助手。用户正在上课，老师提出了一个问题请学生回答。
请给出一个简洁、准确的答案。如果问题涉及计算或推理，请展示步骤。
如果问题不完整，请基于上下文给出最合理的回答。
回答要简洁，适合在课堂环境下快速参考。""",
        icon = "🛡️"
    ),

    // 数学
    MATHEMATICS(
        displayName = "数学",
        keywords = listOf(
            "解", "求", "证明", "计算", "方程", "函数", "导数", "积分",
            "公式", "定理", "推导", "等于", "值", "坐标", "图形",
            "概率", "统计", "数列", "极限", "矩阵"
        ),
        questionPatterns = listOf(
            "求.*", "解.*", "证明.*", "计算.*", ".*等于多少", ".*的值",
            ".*方程", ".*函数", "推导.*", ".*公式"
        ),
        aiSystemPrompt = """你是一个数学学习助手。用户正在上数学课，老师提出了一个数学问题。
请给出详细的解题步骤和最终答案。涉及公式时使用LaTeX格式。
如果是计算题，展示计算过程。如果是证明题，给出清晰的推理步骤。
回答要逻辑清晰，便于课堂快速理解。""",
        icon = "📐"
    ),

    // 物理
    PHYSICS(
        displayName = "物理",
        keywords = listOf(
            "物理", "力", "运动", "速度", "加速度", "能量", "功", "功率",
            "电场", "磁场", "电路", "电阻", "电压", "电流", "光学",
            "波动", "声波", "热", "压强", "浮力", "牛顿"
        ),
        questionPatterns = listOf(
            ".*物理", ".*力.*", ".*运动", ".*速度", ".*能量",
            ".*电场", ".*磁场", ".*电路"
        ),
        aiSystemPrompt = """你是一个物理学习助手。用户正在上物理课，老师提出了一个物理问题。
请给出清晰的物理概念解释，涉及计算时展示公式和计算过程。
使用相关物理定律和原理来解答。回答要适合课堂快速参考。""",
        icon = "⚡"
    ),

    // 化学
    CHEMISTRY(
        displayName = "化学",
        keywords = listOf(
            "化学", "反应", "元素", "化合物", "离子", "分子", "原子",
            "氧化", "还原", "酸碱", "pH", "浓度", "摩尔", "方程式",
            "周期表", "配平", "沉淀", "气体", "实验"
        ),
        questionPatterns = listOf(
            ".*化学", ".*反应", ".*方程式", ".*元素", ".*配平",
            ".*实验", ".*离子", ".*氧化", ".*还原"
        ),
        aiSystemPrompt = """你是一个化学学习助手。用户正在上化学课，老师提出了一个化学问题。
请给出化学方程式、反应原理或实验现象的准确描述。
涉及计算时展示摩尔计算、浓度计算等过程。回答要精确、简洁。""",
        icon = "🧪"
    ),

    // 英语
    ENGLISH(
        displayName = "英语",
        keywords = listOf(
            "translate", "翻译", "grammar", "语法", "vocabulary", "词汇",
            "sentence", "句子", "reading", "阅读", "comprehension", "理解",
            "essay", "作文", "tenses", "时态", "passive", "被动",
            "clause", "从句", "phrase", "短语"
        ),
        questionPatterns = listOf(
            ".*翻译", ".*语法", ".*时态", ".*从句", ".*作文",
            ".*阅读", ".*单词", ".*词性", ".*句子"
        ),
        aiSystemPrompt = """You are an English learning assistant. The user is in an English class and the teacher has asked a question.
Provide clear explanations in Chinese with English examples.
For grammar questions, explain the rules and usage. For translation, provide accurate translations.
Keep answers concise and classroom-appropriate. Use Chinese for explanations with English examples.""",
        icon = "🔤"
    ),

    // 语文
    CHINESE(
        displayName = "语文",
        keywords = listOf(
            "赏析", "分析", "作者", "中心思想", "主旨", "段落", "修辞",
            "比喻", "拟人", "排比", "古诗", "文言文", "翻译", "诗词",
            "作文", "写作", "阅读", "理解", "表达", "手法"
        ),
        questionPatterns = listOf(
            ".*赏析", ".*分析.*文", ".*中心思想", ".*主旨", ".*修辞",
            ".*古诗", ".*文言文", ".*翻译.*文", ".*写作手法"
        ),
        aiSystemPrompt = """你是一个语文学习助手。用户正在上语文课，老师提出了一个问题。
请给出文学作品赏析、文言文翻译、写作手法分析等。
回答要语言优美、分析到位，适合课堂参考。""",
        icon = "📖"
    ),

    // 历史
    HISTORY(
        displayName = "历史",
        keywords = listOf(
            "历史", "事件", "朝代", "战役", "改革", "革命",
            "条约", "制度", "人物", "文明", "时期", "起源",
            "影响", "意义", "背景", "原因", "结果"
        ),
        questionPatterns = listOf(
            ".*历史", ".*事件", ".*朝代", ".*战役", ".*改革",
            ".*革命", ".*条约", ".*制度.*", ".*人物.*"
        ),
        aiSystemPrompt = """你是一个历史学习助手。用户正在上历史课，老师提出了一个历史问题。
请给出历史事件的时间、背景、过程和影响等关键信息。
回答要客观准确，时间线清晰，适合课堂快速参考。""",
        icon = "📜"
    ),

    // 生物
    BIOLOGY(
        displayName = "生物",
        keywords = listOf(
            "生物", "细胞", "基因", "DNA", "遗传", "进化", "生态",
            "光合作用", "呼吸", "酶", "蛋白质", "微生物", "植物",
            "动物", "器官", "系统", "种群", "群落"
        ),
        questionPatterns = listOf(
            ".*生物", ".*细胞", ".*基因", ".*遗传", ".*进化",
            ".*生态", ".*光合作用", ".*呼吸.*", ".*酶"
        ),
        aiSystemPrompt = """你是一个生物学习助手。用户正在上生物课，老师提出了一个生物问题。
请给出准确的生物学概念和原理解释。涉及过程时描述清楚步骤。
回答要科学准确，适合课堂快速参考。""",
        icon = "🧬"
    );

    companion object {
        fun fromName(name: String): SubjectConfig {
            return entries.find { it.name == name || it.displayName == name } ?: GENERAL
        }
    }
}

/**
 * 哨兵运行模式
 */
enum class SentinelMode(val displayName: String, val description: String) {
    CONSERVATIVE("保守模式", "仅在高确信度时（提问概率≥70%）才触发提示，减少打扰"),
    BALANCED("平衡模式", "中等确信度（提问概率≥50%）时触发提示，兼顾准确与及时"),
    AGGRESSIVE("激进模式", "低确信度（提问概率≥30%）即触发提示，不错过任何可能提问")
}

/**
 * AI回答引擎配置
 */
enum class AIEngine(val displayName: String) {
    VOSK_OFFLINE("离线（Vosk本地模型）"),
    ONLINE_API("在线（通过API）")
}

/**
 * 应用设置
 */
data class AppSettings(
    val currentSubject: SubjectConfig = SubjectConfig.GENERAL,
    val sentinelMode: SentinelMode = SentinelMode.BALANCED,
    val aiEngine: AIEngine = AIEngine.ONLINE_API,
    val apiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    val apiKey: String = "",
    val modelName: String = "gpt-3.5-turbo",
    val enableVibration: Boolean = true,
    val enableSound: Boolean = true,
    val enableOverlay: Boolean = true,
    val enableAutoAnswer: Boolean = true,
    val isListening: Boolean = false
)