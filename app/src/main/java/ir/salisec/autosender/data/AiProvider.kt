package ir.salisec.autosender.data

const val GOOGLE_CHAT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
const val GROQ_CHAT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
const val OPENROUTER_CHAT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

/**
 * Every provider here speaks the OpenAI chat-completions dialect, so one client
 * ([ir.salisec.autosender.ai.AiClient]) serves all of them. Adding another
 * OpenAI-compatible provider = one new entry here + one `ProviderConfig` field in [Settings].
 */
enum class AiProvider(
    val id: String,
    /** Full display name (settings screen). */
    val title: String,
    /** Short label for status chips. */
    val shortName: String,
    val defaultEndpoint: String,
    val keyPlaceholder: String,
    /** Page where the user creates a key; blank when there is none to point to. */
    val keyUrl: String,
    /** Free daily quota tier: quota-aware retries and cross-provider fallback matter here. */
    val freeTier: Boolean,
    /** Used only when model discovery fails and nothing is stored yet. */
    val fallbackModel: String,
    val hint: String
) {
    HCNSEC(
        id = "HCNSEC",
        title = "hcnsec",
        shortName = "hcnsec",
        defaultEndpoint = HCNS_CHAT_ENDPOINT,
        keyPlaceholder = "sk-...",
        keyUrl = "",
        freeTier = false,
        fallbackModel = "gpt-4o",
        hint = "پرووایدر اعتباری (سازگار با OpenAI)؛ همان تنظیمات قبلی."
    ),
    GOOGLE(
        id = "GOOGLE",
        title = "Google AI Studio (Gemini)",
        shortName = "Gemini",
        defaultEndpoint = GOOGLE_CHAT_ENDPOINT,
        keyPlaceholder = "AIza...",
        keyUrl = "https://aistudio.google.com/apikey",
        freeTier = true,
        fallbackModel = "gemini-2.5-flash",
        hint = "سطح رایگان با سقف روزانه/دقیقه‌ای جدا برای هر مدل. سقف‌ها زیاد تغییر می‌کنند؛ اگر مدل قوی‌تر سهمیه رایگان نداشته باشد، برنامه خودکار سراغ مدل بعدی می‌رود. توجه: در سطح رایگان، گوگل ممکن است از ورودی‌ها برای بهبود محصولاتش استفاده کند."
    ),
    GROQ(
        id = "GROQ",
        title = "Groq",
        shortName = "Groq",
        defaultEndpoint = GROQ_CHAT_ENDPOINT,
        keyPlaceholder = "gsk_...",
        keyUrl = "https://console.groq.com/keys",
        freeTier = true,
        fallbackModel = "llama-3.3-70b-versatile",
        hint = "سطح رایگان با سقف درخواست و توکن در دقیقه و در روز برای هر مدل؛ بسیار سریع. وقتی سقف روزانهٔ یک مدل پر شود، مدل بعدی امتحان می‌شود."
    ),
    OPENROUTER(
        id = "OPENROUTER",
        title = "OpenRouter (فقط مدل‌های :free)",
        shortName = "OpenRouter",
        defaultEndpoint = OPENROUTER_CHAT_ENDPOINT,
        keyPlaceholder = "sk-or-...",
        keyUrl = "https://openrouter.ai/keys",
        freeTier = true,
        fallbackModel = "meta-llama/llama-3.3-70b-instruct:free",
        hint = "فقط مدل‌های رایگان (:free) انتخاب می‌شوند؛ سقف روزانهٔ مشترک برای همهٔ آن‌ها حدود چند ده درخواست است و با شارژ حداقلی حساب بالا می‌رود. اگر خطای data policy / Free model publication دیدید، در تنظیمات Privacy حساب OpenRouter استفاده از مدل‌های رایگان را مجاز کنید. توجه: ارائه‌دهندگان مدل‌های رایگان ممکن است ورودی را ثبت یا برای آموزش استفاده کنند."
    ),
    );
    companion object {
        fun fromId(id: String?): AiProvider =
            values().firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: HCNSEC
    }
}

/** Key + endpoint + model state of one provider. Stored encrypted by [Prefs]. */
data class ProviderConfig(
    val key: String = "",
    val endpoint: String = "",
    val model: String = "",
    val reviewModel: String = "",
    val verified: Boolean = false,
    /** Comma-separated model ids discovered by the last probe, strongest first. */
    val availableModels: String = ""
) {
    fun availableList(): List<String> =
        availableModels.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Accepts a full chat-completions URL, a base URL ("https://api.groq.com/openai/v1"), or just a
 * host, and returns the full `.../chat/completions` URL. Blank input falls back to [default].
 */
fun normalizeChatEndpoint(raw: String?, default: String): String {
    var v = raw?.trim().orEmpty()
    if (v.isBlank()) return default
    if (!v.startsWith("http://", ignoreCase = true) && !v.startsWith("https://", ignoreCase = true)) {
        v = "https://$v"
    }
    v = v.trimEnd('/')
    if (v.endsWith("/chat/completions")) return v
    val afterScheme = v.substringAfter("://")
    if (!afterScheme.contains('/')) {
        // Host only: keep the provider's own path when it is the same host, otherwise assume /v1.
        val host = afterScheme.lowercase()
        val defaultHost = default.substringAfter("://").substringBefore('/').lowercase()
        return if (host == defaultHost) default else "$v/v1/chat/completions"
    }
    return "$v/chat/completions"
}
