package ir.salisec.autosender.ai

/**
 * What a 429 means for a free-tier provider:
 *  - [daily] = the per-day (or "limit: 0") quota is used up / not granted -> do not retry this model today.
 *  - otherwise it is a per-minute limit -> wait [retryAfterMs] (if the provider said how long) and retry.
 */
internal data class RateLimitInfo(val daily: Boolean, val retryAfterMs: Long?)

// Google: quotaId "GenerateRequestsPerDayPerProjectPerModel-FreeTier" / "limit: 0"
// Groq:   "... on tokens per day (TPD): Limit 100000 ..." / "requests per day (RPD)"
// OpenRouter: "Rate limit exceeded: free-models-per-day ..."
private val DAILY_RX = Regex(
    """per[\s_-]?day|daily|limit:\s*0\b""",
    RegexOption.IGNORE_CASE
)

// Google: "retryDelay": "23s"
private val GOOGLE_RETRY_RX = Regex(
    """retrydelay"?\s*:\s*"?(\d+(?:\.\d+)?)s""",
    RegexOption.IGNORE_CASE
)

// Groq: "Please try again in 7.5s" / "try again in 2m52.8s"
private val GROQ_RETRY_RX = Regex(
    """try again in\s*(?:(\d+(?:\.\d+)?)m)?\s*(\d+(?:\.\d+)?)s""",
    RegexOption.IGNORE_CASE
)

internal fun parseRetryDelayMs(body: String, retryAfterHeader: String?): Long? {
    retryAfterHeader?.trim()?.toDoubleOrNull()?.let { return (it * 1000).toLong() }
    GOOGLE_RETRY_RX.find(body)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
        return (it * 1000).toLong()
    }
    GROQ_RETRY_RX.find(body)?.let { m ->
        val minutes = m.groupValues.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val seconds = m.groupValues.getOrNull(2)?.toDoubleOrNull()
        if (seconds != null) return ((minutes * 60 + seconds) * 1000).toLong()
    }
    return null
}

internal fun classifyRateLimit(body: String, retryAfterHeader: String?): RateLimitInfo =
    RateLimitInfo(
        daily = DAILY_RX.containsMatchIn(body),
        retryAfterMs = parseRetryDelayMs(body, retryAfterHeader)
    )
