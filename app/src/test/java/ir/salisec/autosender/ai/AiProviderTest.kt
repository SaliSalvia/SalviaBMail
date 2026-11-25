package ir.salisec.autosender.ai

import ir.salisec.autosender.data.AiProvider
import ir.salisec.autosender.data.GOOGLE_CHAT_ENDPOINT
import ir.salisec.autosender.data.GROQ_CHAT_ENDPOINT
import ir.salisec.autosender.data.OPENROUTER_CHAT_ENDPOINT
import ir.salisec.autosender.data.CLOUDFLARE_CHAT_ENDPOINT
import ir.salisec.autosender.data.ProviderConfig
import ir.salisec.autosender.data.Settings
import ir.salisec.autosender.data.normalizeChatEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {

    private val client = AiClient(okhttp3.OkHttpClient())

    // ---- endpoints ---------------------------------------------------------------------------

    @Test fun blankEndpointFallsBackToProviderDefault() {
        assertEquals(GROQ_CHAT_ENDPOINT, normalizeChatEndpoint("  ", GROQ_CHAT_ENDPOINT))
        assertEquals(GROQ_CHAT_ENDPOINT, normalizeChatEndpoint(null, GROQ_CHAT_ENDPOINT))
    }

    @Test fun baseUrlGetsChatCompletionsAppended() {
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            normalizeChatEndpoint("https://api.groq.com/openai/v1", GROQ_CHAT_ENDPOINT)
        )
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            normalizeChatEndpoint("api.groq.com/openai/v1/", GROQ_CHAT_ENDPOINT)
        )
    }

    @Test fun fullEndpointIsKeptAndHostOnlyIsHandled() {
        assertEquals(OPENROUTER_CHAT_ENDPOINT, normalizeChatEndpoint(OPENROUTER_CHAT_ENDPOINT, OPENROUTER_CHAT_ENDPOINT))
        // Host-only input for the provider's own host restores its real path (/v1beta/openai/...)
        assertEquals(
            GOOGLE_CHAT_ENDPOINT,
            normalizeChatEndpoint("https://generativelanguage.googleapis.com", GOOGLE_CHAT_ENDPOINT)
        )
        // Unknown host (proxy / relay): assume the usual /v1 layout
        assertEquals(
            "https://my-proxy.example.com/v1/chat/completions",
            normalizeChatEndpoint("https://my-proxy.example.com", GROQ_CHAT_ENDPOINT)
        )
    }

    @Test fun modelsEndpointIsDerivedPerProvider() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/models", client.modelsEndpoint(GOOGLE_CHAT_ENDPOINT))
        assertEquals("https://api.groq.com/openai/v1/models", client.modelsEndpoint(GROQ_CHAT_ENDPOINT))
        assertEquals("https://openrouter.ai/api/v1/models", client.modelsEndpoint(OPENROUTER_CHAT_ENDPOINT))
    }

    // ---- provider selection / fallback chain -----------------------------------------------

    @Test fun cloudflareProviderUsesAccountScopedEndpointsAndRequiresAccountId() {
        assertEquals(
            "https://api.cloudflare.com/client/v4/accounts/account-123/ai/v1/chat/completions",
            client.cloudflareChatEndpoint("account-123")
        )
        assertEquals(
            "https://api.cloudflare.com/client/v4/accounts/account-123/ai/models/search?task=Text%20Generation&per_page=100&hide_experimental=true",
            client.cloudflareModelsEndpoint("account-123")
        )
        val models = client.parseModelList(
            AiProvider.CLOUDFLARE,
            """{"result":[
                {"id":"@cf/meta/llama-3.1-8b-instruct","task":{"name":"Text Generation"}},
                {"name":"meta/llama-3.1-8b-instruct","task":{"name":"Text Generation"}},
                {"id":"@cf/baai/bge-large-en-v1.5","task":{"name":"Text Embeddings"}},
                {"id":"@cf/meta/whisper-large-v3","task":{"name":"Automatic Speech Recognition"}}
            ]}"""
        )
        assertEquals(listOf("@cf/meta/llama-3.1-8b-instruct"), models)
        val valid = Settings(
            aiProvider = AiProvider.CLOUDFLARE.id,
            cloudflare = ProviderConfig(key = "token", accountId = "account-123")
        )
        assertEquals(listOf(AiProvider.CLOUDFLARE), valid.aiProviderChain())
        assertTrue(valid.copy(cloudflare = ProviderConfig(key = "token")).aiProviderChain().isEmpty())
    }

    @Test fun cloudflareModelAccess403TriesNextModelButAuth403SkipsProvider() {
        assertFalse(
            client.shouldSkipProviderForHttpError(
                AiProvider.CLOUDFLARE,
                "Cloudflare HTTP 403: this model requires a paid plan"
            )
        )
        assertTrue(
            client.shouldSkipProviderForHttpError(
                AiProvider.CLOUDFLARE,
                "Cloudflare HTTP 403: insufficient permission for Workers AI"
            )
        )
        assertTrue(client.shouldSkipProviderForHttpError(AiProvider.CLOUDFLARE, "Cloudflare HTTP 401: invalid token"))
        assertTrue(client.shouldSkipProviderForHttpError(AiProvider.GROQ, "Groq HTTP 403: forbidden"))
    }

    @Test fun cloudflareDefaultEndpointIsAccountScopedTemplate() {
        assertTrue(CLOUDFLARE_CHAT_ENDPOINT.contains("{account_id}"))
    }

    @Test fun unknownProviderIdFallsBackToHcnsec() {
        assertEquals(AiProvider.HCNSEC, AiProvider.fromId("nope"))
        assertEquals(AiProvider.GROQ, AiProvider.fromId(" groq "))
    }

    @Test fun chainPutsSelectedProviderFirstThenOtherKeyedOnes() {
        val s = Settings(
            hcnsKey = "k1",
            google = ProviderConfig(key = "g"),
            groq = ProviderConfig(key = "q")
        )
        assertEquals(listOf(AiProvider.HCNSEC, AiProvider.GOOGLE, AiProvider.GROQ), s.aiProviderChain())
        assertEquals(
            listOf(AiProvider.GROQ, AiProvider.HCNSEC, AiProvider.GOOGLE),
            s.copy(aiProvider = "GROQ").aiProviderChain()
        )
    }

    @Test fun chainWithoutFallbackOnlyContainsSelectedProvider() {
        val s = Settings(hcnsKey = "k1", groq = ProviderConfig(key = "q"), aiFallbackEnabled = false)
        assertEquals(listOf(AiProvider.HCNSEC), s.aiProviderChain())
        // selected provider without a key and no fallback = nothing usable
        assertTrue(s.copy(aiProvider = "GOOGLE").aiProviderChain().isEmpty())
    }

    @Test fun selectedProviderWithoutKeyStillFallsBackWhenEnabled() {
        val s = Settings(aiProvider = "GOOGLE", groq = ProviderConfig(key = "q"))
        assertEquals(listOf(AiProvider.GROQ), s.aiProviderChain())
        assertTrue(s.hasAiKey())
    }

    @Test fun noKeysMeansNoAi() {
        assertFalse(Settings().hasAiKey())
        assertTrue(Settings().aiProviderChain().isEmpty())
    }

    @Test fun legacyHcnsFieldsAreTheHcnsecProviderConfig() {
        val s = Settings(hcnsKey = "k", hcnsModel = "gpt-4o", hcnsModelVerified = true, hcnsAvailableModels = "a,b")
        val c = s.config(AiProvider.HCNSEC)
        assertEquals("k", c.key)
        assertEquals("gpt-4o", c.model)
        assertTrue(c.verified)
        assertEquals(listOf("a", "b"), c.availableList())
        val updated = s.withConfig(AiProvider.HCNSEC, c.copy(model = "gpt-5"))
        assertEquals("gpt-5", updated.hcnsModel)
        assertEquals("k", updated.hcnsKey)
    }

    @Test fun probeResultIsStoredOnItsOwnProviderOnly() {
        val s = Settings(hcnsModel = "old", google = ProviderConfig(key = "g"))
        val probe = ModelProbeResult(
            selectedModel = "gemini-2.5-flash",
            reviewModel = "gemini-2.5-pro",
            availableModels = listOf("gemini-2.5-pro", "gemini-2.5-flash"),
            latencyMs = 5,
            probedCandidates = listOf("gemini-2.5-pro", "gemini-2.5-flash"),
            provider = AiProvider.GOOGLE.id
        )
        val next = s.withProbe(probe)
        assertEquals("gemini-2.5-flash", next.google.model)
        assertEquals("gemini-2.5-pro", next.google.reviewModel)
        assertEquals("gemini-2.5-pro,gemini-2.5-flash", next.google.availableModels)
        assertTrue(next.google.verified)
        assertEquals("g", next.google.key)
        assertEquals("old", next.hcnsModel)
    }

    // ---- model discovery + "strongest free model" ranking ------------------------------------

    @Test fun googleListIsStrippedAndNonChatModelsAreDropped() {
        val raw = """{"object":"list","data":[
            {"id":"models/gemini-2.5-pro"},{"id":"models/gemini-2.5-flash"},{"id":"models/gemini-2.5-flash-lite"},
            {"id":"models/gemini-2.5-flash-image"},{"id":"models/gemini-2.5-flash-preview-tts"},
            {"id":"models/gemini-embedding-001"},{"id":"models/text-embedding-004"},
            {"id":"models/imagen-4.0-generate-001"},{"id":"models/gemini-2.0-flash-live-001"},
            {"id":"models/gemma-3-27b-it"},{"id":"models/aqa"}]}"""
        val parsed = client.parseModelList(AiProvider.GOOGLE, raw)
        assertTrue(parsed.none { it.startsWith("models/") })
        assertTrue(parsed.contains("gemini-2.5-pro"))
        assertEquals(
            listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemma-3-27b-it"),
            client.rankModels(parsed)
        )
    }

    @Test fun groqRankingPrefersStrongModelsAndSkipsGuardWhisperCompoundAndRetired() {
        val raw = """{"object":"list","data":[
            {"id":"llama-3.3-70b-versatile","active":true},{"id":"llama-3.1-8b-instant","active":true},
            {"id":"openai/gpt-oss-120b","active":true},{"id":"openai/gpt-oss-20b","active":true},
            {"id":"moonshotai/kimi-k2-instruct-0905","active":true},{"id":"qwen/qwen3-32b","active":true},
            {"id":"meta-llama/llama-4-scout-17b-16e-instruct","active":true},
            {"id":"meta-llama/llama-guard-4-12b","active":true},{"id":"whisper-large-v3","active":true},
            {"id":"playai-tts","active":true},{"id":"groq/compound","active":true},
            {"id":"old-model","active":false}]}"""
        val parsed = client.parseModelList(AiProvider.GROQ, raw)
        assertFalse(parsed.contains("old-model"))
        assertEquals(
            listOf(
                "moonshotai/kimi-k2-instruct-0905",
                "openai/gpt-oss-120b",
                "llama-3.3-70b-versatile",
                "qwen/qwen3-32b",
                "meta-llama/llama-4-scout-17b-16e-instruct",
                "openai/gpt-oss-20b",
                "llama-3.1-8b-instant"
            ),
            client.rankModels(parsed)
        )
    }

    @Test fun openRouterOnlyKeepsFreeTextModels() {
        val raw = """{"data":[
            {"id":"deepseek/deepseek-chat-v3.1:free","pricing":{"prompt":"0","completion":"0"},"architecture":{"output_modalities":["text"]}},
            {"id":"qwen/qwen3-235b-a22b:free","pricing":{"prompt":"0","completion":"0"}},
            {"id":"openai/gpt-oss-120b:free","pricing":{"prompt":"0","completion":"0"}},
            {"id":"anthropic/claude-sonnet-4.5","pricing":{"prompt":"0.000003","completion":"0.000015"}},
            {"id":"openrouter/auto","pricing":{"prompt":"-1","completion":"-1"}},
            {"id":"some/free-by-price","pricing":{"prompt":"0","completion":"0"}},
            {"id":"black-forest-labs/flux-free:free","pricing":{"prompt":"0","completion":"0"},"architecture":{"output_modalities":["image"]}}]}"""
        val parsed = client.parseModelList(AiProvider.OPENROUTER, raw)
        assertEquals(
            listOf(
                "deepseek/deepseek-chat-v3.1:free",
                "qwen/qwen3-235b-a22b:free",
                "openai/gpt-oss-120b:free",
                "some/free-by-price"
            ),
            parsed
        )
        assertEquals(
            listOf(
                "openai/gpt-oss-120b:free",
                "deepseek/deepseek-chat-v3.1:free",
                "qwen/qwen3-235b-a22b:free",
                "some/free-by-price"
            ),
            client.rankModels(parsed)
        )
    }

    @Test fun paidProviderListsAreNotFilteredByPrice() {
        val raw = """{"data":[{"id":"gpt-4o","pricing":{"prompt":"0.000005","completion":"0.000015"}},{"id":"text-embedding-3-small"}]}"""
        val parsed = client.parseModelList(AiProvider.HCNSEC, raw)
        assertEquals(listOf("gpt-4o"), client.rankModels(parsed))
    }

    @Test fun existingFlagshipOrderingIsUnchanged() {
        val ids = listOf("gpt-4o-mini", "gpt-4o", "claude-sonnet-4-5", "claude-opus-4-6", "gemini-2.5-pro", "deepseek-chat")
        assertEquals(
            listOf("claude-opus-4-6", "claude-sonnet-4-5", "gemini-2.5-pro", "gpt-4o", "deepseek-chat", "gpt-4o-mini"),
            client.rankModels(ids)
        )
    }

    // ---- free-tier quota / rate-limit handling ---------------------------------------------

    @Test fun googleDailyQuotaIsDetected() {
        val body = """{"error":{"code":429,"message":"You exceeded your current quota","status":"RESOURCE_EXHAUSTED",
            "details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]},
            {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"23s"}]}}"""
        val rl = classifyRateLimit(body, null)
        assertTrue(rl.daily)
        assertEquals(23_000L, rl.retryAfterMs)
    }

    @Test fun googleZeroLimitMeansModelHasNoFreeQuota() {
        val body = "Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-2.5-pro"
        assertTrue(classifyRateLimit(body, null).daily)
    }

    @Test fun groqPerMinuteLimitIsRetriedNotBlacklisted() {
        val body = "Rate limit reached for model `llama-3.3-70b-versatile` in organization `org_x` service tier `on_demand` " +
            "on tokens per minute (TPM): Limit 12000, Used 11000, Requested 3000. Please try again in 7.5s. " +
            "Need more tokens? Upgrade to Dev Tier today at https://console.groq.com/settings/billing"
        val rl = classifyRateLimit(body, null)
        assertFalse(rl.daily)
        assertEquals(7_500L, rl.retryAfterMs)
    }

    @Test fun groqDailyLimitWithMinutesIsParsed() {
        val body = "Rate limit reached ... on tokens per day (TPD): Limit 100000, Used 99000, Requested 2000. Please try again in 2m52.8s."
        val rl = classifyRateLimit(body, null)
        assertTrue(rl.daily)
        assertTrue("retry ms was ${rl.retryAfterMs}", (rl.retryAfterMs ?: 0L) in 172_000L..173_000L)
    }

    @Test fun openRouterFreeModelsDailyCapIsDetected() {
        val body = """{"error":{"message":"Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model requests a day.","code":429}}"""
        assertTrue(classifyRateLimit(body, null).daily)
    }

    @Test fun retryAfterHeaderWinsAndPlainErrorsHaveNoDelay() {
        val rl = classifyRateLimit("slow down", "12")
        assertFalse(rl.daily)
        assertEquals(12_000L, rl.retryAfterMs)
        assertNull(classifyRateLimit("slow down", null).retryAfterMs)
    }

    // ---- provider quirks --------------------------------------------------------------------

    @Test fun reasoningEffortIsOnlySentWhereSupported() {
        assertEquals(mapOf("reasoning_effort" to "low"), client.extraParams(AiProvider.GOOGLE, "gemini-2.5-flash"))
        assertEquals(mapOf("reasoning_effort" to "low"), client.extraParams(AiProvider.GROQ, "openai/gpt-oss-120b"))
        assertEquals(mapOf("reasoning_effort" to "none"), client.extraParams(AiProvider.GROQ, "qwen/qwen3-32b"))
        assertTrue(client.extraParams(AiProvider.GROQ, "llama-3.3-70b-versatile").isEmpty())
        assertTrue(client.extraParams(AiProvider.HCNSEC, "gpt-4o").isEmpty())
        assertTrue(client.extraParams(AiProvider.OPENROUTER, "deepseek/deepseek-r1:free").isEmpty())
    }

    @Test fun thinkingModelsGetRoomForTheAnswerOnFreeProviders() {
        assertEquals(6000, client.effectiveMaxTokens(AiProvider.GOOGLE, "gemini-2.5-flash", 2800))
        assertEquals(6000, client.effectiveMaxTokens(AiProvider.OPENROUTER, "deepseek/deepseek-r1:free", 2800))
        assertEquals(2800, client.effectiveMaxTokens(AiProvider.GROQ, "llama-3.3-70b-versatile", 2800))
        // paid hcnsec behaviour is untouched
        assertEquals(2800, client.effectiveMaxTokens(AiProvider.HCNSEC, "o3", 2800))
    }

    @Test fun gemini3UsesDefaultTemperature() {
        assertEquals(1.0, client.effectiveTemperature(AiProvider.GOOGLE, "gemini-3-pro-preview", 0.4), 0.0)
        assertEquals(0.4, client.effectiveTemperature(AiProvider.GOOGLE, "gemini-2.5-flash", 0.4), 0.0)
        assertEquals(0.4, client.effectiveTemperature(AiProvider.GROQ, "llama-3.3-70b-versatile", 0.4), 0.0)
    }
}
