package ir.salisec.autosender.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import ir.salisec.autosender.App
import ir.salisec.autosender.R
import ir.salisec.autosender.ai.withProbe
import ir.salisec.autosender.data.AiProvider
import ir.salisec.autosender.data.Prefs
import ir.salisec.autosender.data.ProviderConfig
import ir.salisec.autosender.data.ReportReader
import ir.salisec.autosender.mail.EmailAutoconfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    app: App,
    onBack: () -> Unit,
    onOpenEmailSetup: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(app.prefs.load()) }
    var maxAiCallsStr by remember { mutableStateOf(s.maxAiCallsPerEmail.toString()) }
    var youCountStr by remember { mutableStateOf(s.youCount.toString()) }
    var smtpPortStr by remember { mutableStateOf(s.smtpPort.toString()) }
    var delayStr by remember { mutableStateOf(s.delaySeconds.toString()) }
    var jitterStr by remember { mutableStateOf(s.jitterSeconds.toString()) }
    var dailyCapStr by remember { mutableStateOf(s.dailyCap.toString()) }
    var bizStartStr by remember { mutableStateOf(s.businessStartHour.toString()) }
    var bizEndStr by remember { mutableStateOf(s.businessEndHour.toString()) }
    var fu1Str by remember { mutableStateOf(s.followUpDays1.toString()) }
    var fu2Str by remember { mutableStateOf(s.followUpDays2.toString()) }
    var maxMbStr by remember { mutableStateOf(s.maxAttachmentMb.toString()) }
    var imapPortStr by remember { mutableStateOf(s.imapPort.toString()) }

    fun syncNumberStrings(src: ir.salisec.autosender.data.Settings) {
        maxAiCallsStr = src.maxAiCallsPerEmail.toString()
        youCountStr = src.youCount.toString()
        smtpPortStr = src.smtpPort.toString()
        delayStr = src.delaySeconds.toString()
        jitterStr = src.jitterSeconds.toString()
        dailyCapStr = src.dailyCap.toString()
        bizStartStr = src.businessStartHour.toString()
        bizEndStr = src.businessEndHour.toString()
        fu1Str = src.followUpDays1.toString()
        fu2Str = src.followUpDays2.toString()
        maxMbStr = src.maxAttachmentMb.toString()
        imapPortStr = src.imapPort.toString()
    }

    fun normalizedSettings(markAccountSetup: Boolean = false): ir.salisec.autosender.data.Settings {
        val next = s.copy(
            accountSetupDone = if (markAccountSetup) true else s.accountSetupDone,
            maxAiCallsPerEmail = (maxAiCallsStr.trim().toIntOrNull() ?: s.maxAiCallsPerEmail).coerceIn(1, 3),
            youCount = (youCountStr.trim().toIntOrNull() ?: s.youCount).coerceIn(1, 10),
            smtpPort = (smtpPortStr.trim().toIntOrNull() ?: s.smtpPort).coerceIn(1, 65535),
            delaySeconds = (delayStr.trim().toIntOrNull() ?: s.delaySeconds).coerceIn(15, 600),
            jitterSeconds = (jitterStr.trim().toIntOrNull() ?: s.jitterSeconds).coerceIn(0, 120),
            dailyCap = (dailyCapStr.trim().toIntOrNull() ?: s.dailyCap).coerceIn(1, 500),
            businessStartHour = (bizStartStr.trim().toIntOrNull() ?: s.businessStartHour).coerceIn(0, 23),
            businessEndHour = (bizEndStr.trim().toIntOrNull() ?: s.businessEndHour).coerceIn(1, 24),
            followUpDays1 = (fu1Str.trim().toIntOrNull() ?: s.followUpDays1).coerceIn(1, 30),
            followUpDays2 = (fu2Str.trim().toIntOrNull() ?: s.followUpDays2).coerceIn(1, 60),
            maxAttachmentMb = (maxMbStr.trim().toIntOrNull() ?: s.maxAttachmentMb).coerceIn(1, 4),
            imapPort = (imapPortStr.trim().toIntOrNull() ?: s.imapPort).coerceIn(1, 65535)
        )
        s = next
        syncNumberStrings(next)
        return next
    }

    var saved by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    var erased by remember { mutableStateOf(false) }

    var probingAi by remember { mutableStateOf(false) }
    var aiStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var testingYou by remember { mutableStateOf(false) }
    var youStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var testingMail by remember { mutableStateOf(false) }
    var mailStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var folderCountText by remember { mutableStateOf("") }

    var k9Notice by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val k9Picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val xml = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    } catch (_: Exception) { "" }
                }
                val parsed = EmailAutoconfig.parseK9Settings(xml)
                if (parsed == null) {
                    k9Notice = false to "فایل .k9s معتبر پیدا نشد یا سرورهای IMAP/SMTP در آن ثبت نشده است."
                } else {
                    val nextEmail = parsed.email.ifBlank { s.fromEmail }
                    val updated = s.copy(
                        fromEmail = nextEmail,
                        replyTo = s.replyTo.ifBlank { nextEmail },
                        sendMode = if (s.brevoKey.isNotBlank()) s.sendMode else "SMTP",
                        imapHost = parsed.imapHost.ifBlank { s.imapHost },
                        imapPort = parsed.imapPort,
                        imapSecurity = parsed.imapSecurity,
                        imapUser = parsed.imapUsername.ifBlank { nextEmail },
                        smtpHost = parsed.smtpHost.ifBlank { s.smtpHost },
                        smtpPort = parsed.smtpPort,
                        smtpSecurity = parsed.smtpSecurity,
                        smtpUser = parsed.smtpUsername.ifBlank { nextEmail }
                    )
                    s = updated
                    syncNumberStrings(updated)
                    saved = false
                    k9Notice = true to "تنظیمات فایل .k9s ایمپورت شد (${ltrIsolate(updated.smtpHost)}). رمز صندوق ایمیل را وارد و ذخیره کنید."
                }
            }
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val updated = s.copy(sitestFolderUri = uri.toString())
            s = updated
            app.prefs.save(updated)
            saved = true
            scope.launch {
                val count = withContext(Dispatchers.IO) {
                    val doc = DocumentFile.fromTreeUri(context, uri)
                    if (doc != null) ReportReader.indexSubfolders(doc).size else 0
                }
                folderCountText = "$count پوشه دامنه شناسایی شد"
            }
        }
    }

    val discoveredModels = remember(s.aiProvider, s.config(s.provider()).availableModels) {
        s.config(s.provider()).availableList()
    }

    ScreenScaffold {
        SubTitle("تمام کلیدهای API، قالب‌های ایمیل و مشخصات ایمیل فقط یک‌بار وارد شده و به صورت رمزنگاری‌شده (AES) دائماً ذخیره می‌شوند.")

        // 0. Quick Thunderbird Email Setup Wizard, .k9s Import & Runflare Preset
        OutlinedButton(
            onClick = onOpenEmailSetup,
            modifier = Modifier.fillMaxWidth().testTag("settings_open_thunderbird")
        ) {
            Text("راه‌اندازی جادویی ایمیل ورودی و خروجی (سبک Thunderbird)")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val preset = EmailAutoconfig.runflarePreset(s.fromEmail)
                    val emailUser = s.fromEmail.trim().ifBlank { s.smtpUser.ifBlank { s.imapUser } }
                    val updated = s.copy(
                        sendMode = "SMTP",
                        smtpHost = preset.smtpHost,
                        smtpPort = preset.smtpPort,
                        smtpSecurity = preset.smtpSecurity,
                        smtpUser = emailUser,
                        imapHost = preset.imapHost,
                        imapPort = preset.imapPort,
                        imapSecurity = preset.imapSecurity,
                        imapUser = emailUser,
                        dkimSelector = s.dkimSelector.ifBlank { "runflare" }
                    )
                    s = updated
                    syncNumberStrings(updated)
                    saved = false
                    k9Notice = true to "پریست Runflare اعمال شد (${ltrIsolate("smtp-ir-01.runflare.com")} • IMAP 993 / SMTP 465 SSL). رمز صندوق ایمیل را وارد کنید."
                },
                modifier = Modifier.weight(1f).testTag("settings_runflare_preset")
            ) {
                Text("پریست Runflare")
            }
            OutlinedButton(
                onClick = { k9Picker.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f).testTag("settings_import_k9s")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Text(" ورود فایل .k9s")
            }
        }
        k9Notice?.let { (ok, msg) -> Chip(msg, if (ok) GreenOk else RedErr) }

        // 1. AI provider (hcnsec / Google AI Studio / Groq / OpenRouter) + live model probe
        val prov = s.provider()
        val cfg = s.config(prov)
        fun updateCfg(change: (ProviderConfig) -> ProviderConfig) {
            s = s.withConfig(prov, change(s.config(prov)))
            saved = false
        }
        Section(
            header = "۱. هوش مصنوعی (انتخاب پرووایدر)",
            icon = Icons.Default.AutoAwesome,
            badge = if (cfg.verified && cfg.model.isNotBlank()) cfg.model else if (cfg.key.isNotBlank()) "اتصال آماده" else "نیاز به کلید/شناسه",
            badgeColor = if (cfg.verified) GreenOk else if (cfg.key.isNotBlank()) OrangeWarn else RedErr
        ) {
            SubTitle("پرووایدر فعال را انتخاب کنید؛ برای هر پرووایدر کلید و مدل جدا ذخیره می‌شود. فقط پرووایدرهای دارای اطلاعات کامل اتصال در فال‌بک خودکار شرکت می‌کنند.")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiProvider.values().forEach { p ->
                    FilterChip(
                        selected = prov == p,
                        onClick = {
                            s = s.copy(aiProvider = p.id)
                            saved = false
                            aiStatus = null
                        },
                        label = {
                            val pc = s.config(p)
                            val ready = pc.key.isNotBlank() && true
                            Text(p.shortName + if (ready) " ✓" else "")
                        }
                    )
                }
            }
            SubTitle(prov.hint)
            if (prov.keyUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(prov.keyUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (prov.freeTier) "دریافت کلید رایگان ${prov.shortName}" else "دریافت API Token ${prov.shortName}")
                }
            }

            LabeledField(
                "کلید API ${prov.title} (${prov.keyPlaceholder})",
                cfg.key,
                isSecret = true,
                modifier = Modifier.testTag("settings_ai_key")
            ) { v ->
                updateCfg { c -> c.copy(key = v, verified = false) }
            }
            LabeledField("آدرس Endpoint", cfg.endpoint, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) { v ->
                updateCfg { c -> c.copy(endpoint = v, verified = false) }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = s.autoSelectBestModel,
                    onClick = { s = s.copy(autoSelectBestModel = !s.autoSelectBestModel); saved = false },
                    label = { Text("انتخاب خودکار قوی‌ترین مدل") }
                )
                FilterChip(
                    selected = s.enableCriticPass,
                    onClick = { s = s.copy(enableCriticPass = !s.enableCriticPass); saved = false },
                    label = { Text("بازبینی ویراستار فروش (همیشه فعال)") }
                )
                FilterChip(
                    selected = s.aiFallbackEnabled,
                    onClick = { s = s.copy(aiFallbackEnabled = !s.aiFallbackEnabled); saved = false },
                    label = { Text("فال‌بک خودکار به پرووایدرهای دیگر") }
                )
            }

            BigButton(
                text = if (probingAi) "در حال سنجش مدل‌های ${prov.shortName}..." else "سنجش API و انتخاب قوی‌ترین مدل ${if (prov.freeTier) "رایگان " else ""}در دسترس",
                loading = probingAi,
                enabled = cfg.key.isNotBlank(),
                modifier = Modifier.testTag("settings_probe_ai")
            ) {
                scope.launch {
                    probingAi = true
                    aiStatus = null
                    val curr = normalizedSettings()
                    app.prefs.save(curr)
                    val res = app.ai.probeAndSelectBestModel(app.prefs.load(), prov)
                    res.onSuccess { probe ->
                        val next = curr.withProbe(probe)
                        s = next
                        app.prefs.save(next)
                        saved = true
                        aiStatus = if (probe.verified) {
                            true to "قوی‌ترین مدل فعال: ${probe.selectedModel} (${probe.latencyMs}ms | ${probe.availableModels.size} مدل موجود)"
                        } else {
                            false to "هیچ مدلی به تست پاسخ نداد؛ مدل ${probe.selectedModel} فقط به‌عنوان پیش‌فرض ذخیره شد. کلید/اینترنت/سهمیه را بررسی کنید."
                        }
                    }.onFailure { err ->
                        aiStatus = false to (err.message?.take(220) ?: "خطا در سنجش API")
                    }
                    probingAi = false
                }
            }

            aiStatus?.let { (ok, msg) -> Chip(msg, if (ok) GreenOk else RedErr) }

            LabeledField("مدل فعال تولید متن", cfg.model, placeholder = "با دکمه بالا خودکار انتخاب می‌شود") { v ->
                updateCfg { c -> c.copy(model = v) }
            }

            if (discoveredModels.isNotEmpty()) {
                SubTitle("مدل‌های برتر یافت‌شده از API (کلیک برای تغییر دستی):")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    discoveredModels.take(8).forEach { m ->
                        FilterChip(
                            selected = cfg.model == m,
                            onClick = {
                                val next = s.withConfig(prov, s.config(prov).copy(model = m, verified = true))
                                s = next
                                app.prefs.save(next)
                                saved = true
                            },
                            label = { Text(m) }
                        )
                    }
                }
            }

            LabeledField(
                label = "حداکثر پاس کیفیت برای هر ایمیل (۳ = بالاترین کیفیت؛ با سهمیه رایگان ۱ یا ۲ توصیه می‌شود)",
                value = maxAiCallsStr,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            ) { raw ->
                maxAiCallsStr = raw.filter { it.isDigit() }.take(1)
                saved = false
            }
        }

        // 1.5 Four Reference Templates for AI Structure & Persuasion Guidance
        val activeTemplateCount = s.referenceTemplates().size
        Section(
            header = "۲. ۴ الگوی مرجع ایمیل برای هوش مصنوعی",
            icon = Icons.Default.Description,
            badge = "$activeTemplateCount از ۴ الگو فعال",
            badgeColor = if (activeTemplateCount > 0) GreenOk else MaterialTheme.colorScheme.primary
        ) {
            SubTitle("این ۴ متن صرفاً به عنوان الگوی ساختار، لحن و ریتم نگارش به مدل هوش مصنوعی داده می‌شوند. هوش مصنوعی عین آن‌ها را کپی نمی‌کند؛ بلکه با اطلاعات زنده You.com و PDFها برای هر شرکت پیام اختصاصی می‌سازد.")

            LabeledField(
                label = "الگوی ۱ (مثلاً ایمیل معرفی با قلاب کسب‌وکار)",
                value = s.template1,
                multiline = true,
                placeholder = "نمونه ایمیل اول خود را اینجا وارد کنید...",
                modifier = Modifier.testTag("settings_template_1")
            ) { s = s.copy(template1 = it); saved = false }

            LabeledField(
                label = "الگوی ۲ (مثلاً ایمیل کوتاه و مستقیم برای مدیرعامل)",
                value = s.template2,
                multiline = true,
                placeholder = "نمونه ایمیل دوم خود را اینجا وارد کنید...",
                modifier = Modifier.testTag("settings_template_2")
            ) { s = s.copy(template2 = it); saved = false }

            LabeledField(
                label = "الگوی ۳ (مثلاً لحن تخصصی برای مدیر فنی)",
                value = s.template3,
                multiline = true,
                placeholder = "نمونه ایمیل سوم خود را اینجا وارد کنید...",
                modifier = Modifier.testTag("settings_template_3")
            ) { s = s.copy(template3 = it); saved = false }

            LabeledField(
                label = "الگوی ۴ (مثلاً ساختار متمرکز بر ROI و P.S.)",
                value = s.template4,
                multiline = true,
                placeholder = "نمونه ایمیل چهارم خود را اینجا وارد کنید...",
                modifier = Modifier.testTag("settings_template_4")
            ) { s = s.copy(template4 = it); saved = false }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        s = s.copy(
                            template1 = s.template1.ifBlank { Prefs.SAMPLE_TEMPLATES[0] },
                            template2 = s.template2.ifBlank { Prefs.SAMPLE_TEMPLATES[1] },
                            template3 = s.template3.ifBlank { Prefs.SAMPLE_TEMPLATES[2] },
                            template4 = s.template4.ifBlank { Prefs.SAMPLE_TEMPLATES[3] }
                        )
                        saved = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("درج نمونه‌های پیشنهادی")
                }
                OutlinedButton(
                    onClick = {
                        app.prefs.save(normalizedSettings())
                        saved = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ذخیره الگوها")
                }
            }
        }

        // 3. You.com Search API
        Section(
            header = "۳. جستجوی کسب‌وکار You.com",
            icon = Icons.Default.Search,
            badge = if (s.youVerified) "متصل" else if (s.youKey.isNotBlank()) "ثبت‌شده" else "نیاز به کلید",
            badgeColor = if (s.youVerified) GreenOk else if (s.youKey.isNotBlank()) OrangeWarn else RedErr
        ) {
            LabeledField("کلید API سایت You.com", s.youKey, isSecret = true, modifier = Modifier.testTag("settings_you_key")) {
                s = s.copy(youKey = it, youVerified = false)
                saved = false
            }
            LabeledField("Endpoint جستجو", s.youEndpoint, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) {
                s = s.copy(youEndpoint = it, youVerified = false)
                saved = false
            }
            LabeledField(
                label = "تعداد نتایج برای هر دامنه (۱ تا ۱۰)",
                value = youCountStr,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            ) { raw ->
                youCountStr = raw.filter { it.isDigit() }.take(2)
                saved = false
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        testingYou = true
                        youStatus = null
                        val curr = normalizedSettings()
                        app.prefs.save(curr)
                        val res = app.enricher.testConnection(app.prefs.load())
                        res.onSuccess { msg ->
                            val next = curr.copy(youVerified = true)
                            s = next
                            app.prefs.save(next)
                            saved = true
                            youStatus = true to msg
                        }.onFailure { err ->
                            youStatus = false to (err.message?.take(160) ?: "خطا در اتصال به You.com")
                        }
                        testingYou = false
                    }
                },
                enabled = s.youKey.isNotBlank() && !testingYou,
                modifier = Modifier.fillMaxWidth().testTag("settings_test_you")
            ) {
                Text(if (testingYou) "در حال تست You.com..." else "تست و ذخیره اتصال You.com")
            }
            youStatus?.let { (ok, msg) -> Chip(msg, if (ok) GreenOk else RedErr) }
        }

        // 4. sitest Folder (Persistent SAF)
        Section(
            header = "۴. پوشه گزارش‌های sitest در حافظه گوشی",
            icon = Icons.Default.FolderOpen,
            badge = if (s.sitestFolderUri.isNotBlank()) "متصل دائمی" else "انتخاب نشده",
            badgeColor = if (s.sitestFolderUri.isNotBlank()) GreenOk else OrangeWarn
        ) {
            SubTitle("مسیر مورد انتظار: Internal storage > sitest > [domain] > 2 PDFs. یک‌بار پوشه sitest را انتخاب کنید تا برای همیشه ذخیره بماند.")
            if (folderCountText.isNotBlank()) Chip(folderCountText, GreenOk)
            OutlinedButton(
                onClick = {
                    val initUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ReportReader.initialSitestTreeUri()
                    } else null
                    treePicker.launch(initUri)
                },
                modifier = Modifier.fillMaxWidth().testTag("settings_pick_sitest")
            ) {
                Text(if (s.sitestFolderUri.isBlank()) "انتخاب پوشه sitest" else "تغییر پوشه sitest")
            }
        }

        // 5. Sender Identity & Mail Transport
        Section(
            header = "۵. هویت فرستنده و سرویس ارسال خروجی",
            icon = Icons.Default.Email,
            badge = s.sendMode,
            badgeColor = MaterialTheme.colorScheme.primary
        ) {
            LabeledField("نام نمایشی فرستنده", s.fromName) { s = s.copy(fromName = it); saved = false }
            LabeledField("ایمیل فرستنده", s.fromEmail, placeholder = "security@yourdomain.com", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) {
                s = s.copy(fromEmail = it); saved = false
            }
            LabeledField("Reply-To (اختیاری)", s.replyTo, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) { s = s.copy(replyTo = it); saved = false }
            LabeledField("Selector اختصاصی DKIM (اختیاری، مثلاً mail یا brevo1)", s.dkimSelector) {
                s = s.copy(dkimSelector = it); saved = false
            }
            LabeledField("امضای انتهای ایمیل", s.signature, multiline = true) { s = s.copy(signature = it); saved = false }
            LabeledField("متن خروج از فهرست (opt-out)", s.unsubscribeFooter, multiline = true) {
                s = s.copy(unsubscribeFooter = it); saved = false
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.sendMode == "BREVO_API",
                    onClick = { s = s.copy(sendMode = "BREVO_API"); saved = false },
                    label = { Text("Brevo API") }
                )
                FilterChip(
                    selected = s.sendMode == "SMTP",
                    onClick = { s = s.copy(sendMode = "SMTP"); saved = false },
                    label = { Text("SMTP اختصاصی") }
                )
            }
            if (s.sendMode == "BREVO_API") {
                LabeledField("کلید Brevo API (xkeysib-...)", s.brevoKey, isSecret = true) {
                    s = s.copy(brevoKey = it); saved = false
                }
            } else {
                LabeledField("SMTP Host", s.smtpHost, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) { s = s.copy(smtpHost = it); saved = false }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(
                        "SMTP Port",
                        smtpPortStr,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    ) { raw ->
                        smtpPortStr = raw.filter { it.isDigit() }.take(5); saved = false
                    }
                    Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubTitle("امنیت SMTP (پورت خودکار)")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = s.smtpSecurity == "STARTTLS",
                                onClick = {
                                    val newPort = if (smtpPortStr == "465" || smtpPortStr.isBlank()) "587" else smtpPortStr
                                    smtpPortStr = newPort
                                    s = s.copy(smtpSecurity = "STARTTLS", smtpPort = newPort.toIntOrNull() ?: 587)
                                    saved = false
                                },
                                label = { Text("STARTTLS") }
                            )
                            FilterChip(
                                selected = s.smtpSecurity == "SSL",
                                onClick = {
                                    val newPort = if (smtpPortStr == "587" || smtpPortStr.isBlank()) "465" else smtpPortStr
                                    smtpPortStr = newPort
                                    s = s.copy(smtpSecurity = "SSL", smtpPort = newPort.toIntOrNull() ?: 465)
                                    saved = false
                                },
                                label = { Text("SSL") }
                            )
                        }
                    }
                }
                LabeledField("نام کاربری SMTP (کامل با @)", s.smtpUser) { s = s.copy(smtpUser = it); saved = false }
                LabeledField("رمز صندوق ایمیل / SMTP", s.smtpPass, isSecret = true) { s = s.copy(smtpPass = it); saved = false }
                if (s.smtpPass.isNotBlank() && s.imapPass != s.smtpPass) {
                    TextButton(onClick = {
                        s = s.copy(
                            imapPass = s.smtpPass,
                            imapUser = s.imapUser.ifBlank { s.smtpUser.ifBlank { s.fromEmail } }
                        )
                        saved = false
                    }) {
                        Text("کپی همین نام کاربری و رمز برای IMAP نیز")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    "فاصله ارسال (ثانیه)",
                    delayStr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                ) { raw ->
                    delayStr = raw.filter { it.isDigit() }.take(3); saved = false
                }
                LabeledField(
                    "نوسان تصادفی",
                    jitterStr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                ) { raw ->
                    jitterStr = raw.filter { it.isDigit() }.take(3); saved = false
                }
                LabeledField(
                    "سقف روزانه",
                    dailyCapStr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                ) { raw ->
                    dailyCapStr = raw.filter { it.isDigit() }.take(3); saved = false
                }
            }

            FilterChip(
                selected = s.enforceBusinessHours,
                onClick = { s = s.copy(enforceBusinessHours = !s.enforceBusinessHours); saved = false },
                label = { Text("ارسال فقط در ساعات اداری (پیشنهاد B2B)") }
            )
            if (s.enforceBusinessHours) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(
                        "شروع ساعت کاری (۰-۲۳)",
                        bizStartStr,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    ) { raw ->
                        bizStartStr = raw.filter { it.isDigit() }.take(2); saved = false
                    }
                    LabeledField(
                        "پایان ساعت کاری (۱-۲۴)",
                        bizEndStr,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    ) { raw ->
                        bizEndStr = raw.filter { it.isDigit() }.take(2); saved = false
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    "یادآور پیگیری اول (روز)",
                    fu1Str,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                ) { raw ->
                    fu1Str = raw.filter { it.isDigit() }.take(2); saved = false
                }
                LabeledField(
                    "یادآور پیگیری دوم (روز)",
                    fu2Str,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                ) { raw ->
                    fu2Str = raw.filter { it.isDigit() }.take(2); saved = false
                }
            }
        }

        // 6. Attachment Policy
        Section(header = stringResource(R.string.policy_title), icon = Icons.Default.Security) {
            Chip(stringResource(R.string.policy_mandatory), GreenOk)
            LabeledField(
                stringResource(R.string.policy_cap),
                maxMbStr,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            ) { raw ->
                maxMbStr = raw.filter { it.isDigit() }.take(1); saved = false
            }
            SubTitle(stringResource(R.string.policy_note))
        }

        // 7. IMAP & Opt-out + Live Verifier
        Section(header = "۶. صندوق ورودی (IMAP) و تست اتصال ایمیل", icon = Icons.Default.MarkEmailRead) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField("IMAP Host", s.imapHost, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(2f)) {
                    s = s.copy(imapHost = it); saved = false
                }
                LabeledField(
                    "Port",
                    imapPortStr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                ) { raw ->
                    imapPortStr = raw.filter { it.isDigit() }.take(5); saved = false
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.imapSecurity == "SSL",
                    onClick = {
                        val newPort = if (imapPortStr == "143" || imapPortStr.isBlank()) "993" else imapPortStr
                        imapPortStr = newPort
                        s = s.copy(imapSecurity = "SSL", imapPort = newPort.toIntOrNull() ?: 993)
                        saved = false
                    },
                    label = { Text("SSL (993)") }
                )
                FilterChip(
                    selected = s.imapSecurity == "STARTTLS",
                    onClick = {
                        val newPort = if (imapPortStr == "993" || imapPortStr.isBlank()) "143" else imapPortStr
                        imapPortStr = newPort
                        s = s.copy(imapSecurity = "STARTTLS", imapPort = newPort.toIntOrNull() ?: 143)
                        saved = false
                    },
                    label = { Text("STARTTLS (143)") }
                )
            }
            LabeledField("نام کاربری IMAP (کامل با @)", s.imapUser) { s = s.copy(imapUser = it); saved = false }
            LabeledField("رمز صندوق ایمیل (IMAP Password)", s.imapPass, isSecret = true) {
                s = s.copy(imapPass = it); saved = false
            }
            if (s.sendMode == "SMTP" && s.imapPass.isNotBlank() && s.smtpPass != s.imapPass) {
                TextButton(onClick = {
                    s = s.copy(
                        smtpPass = s.imapPass,
                        smtpUser = s.smtpUser.ifBlank { s.imapUser.ifBlank { s.fromEmail } }
                    )
                    saved = false
                }) {
                    Text("کپی همین نام کاربری و رمز برای SMTP نیز")
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        testingMail = true
                        mailStatus = null
                        val current = normalizedSettings(markAccountSetup = true)
                        app.prefs.save(current)
                        val res = EmailAutoconfig.verifyIncomingAndOutgoing(
                            client = app.ok,
                            s = current,
                            testIncoming = current.imapHost.isNotBlank() && current.imapPass.isNotBlank(),
                            testOutgoing = current.hasOutgoingConfigured()
                        )
                        mailStatus = res.allOk to "${res.incomingMessage}\n${res.outgoingMessage}"
                        testingMail = false
                    }
                },
                enabled = !testingMail,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (testingMail) "در حال تست ورود ورودی + خروجی..." else "تست زنده اتصال ورودی (IMAP) و خروجی")
            }
            mailStatus?.let { (ok, msg) -> Chip(msg, if (ok) GreenOk else RedErr) }

            val suppressedCount = s.suppressed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.size
            SubTitle("$suppressedCount ایمیل/دامنه در لیست عدم ارسال (opt-out) ثبت شده است.")
            LabeledField("لیست عدم ارسال (با کاما جدا کنید)", s.suppressed, multiline = true) {
                s = s.copy(suppressed = it); saved = false
            }
        }

        BigButton(
            text = "ذخیره دائمی همه تنظیمات و الگوها",
            icon = Icons.Default.Save,
            modifier = Modifier.testTag("settings_save_button")
        ) {
            app.prefs.save(normalizedSettings(markAccountSetup = true))
            saved = true
        }
        if (saved) Chip("تنظیمات و الگوها با موفقیت رمزنگاری و ذخیره شد ✓", GreenOk)

        Section(header = "حریم خصوصی و پاکسازی", icon = Icons.Default.DeleteForever) {
            SubTitle("پاکسازی، همه پیام‌ها، لاگ‌ها، کلیدهای API، الگوها و تنظیمات ذخیره شده را از دستگاه حذف می‌کند.")
            OutlinedButton(
                onClick = { confirmErase = true },
                enabled = !erased,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("پاکسازی کامل داده‌های برنامه", color = MaterialTheme.colorScheme.error)
            }
            if (erased) Chip("همه داده‌های محلی پاک شد ✓", GreenOk)
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("پاکسازی کامل داده‌ها؟") },
            text = { Text("این عملیات برگشت‌ناپذیر است و تمام پیام‌ها، صف ارسال، کلیدهای API و تنظیمات پاک می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    scope.launch {
                        app.clearAllData()
                        val fresh = app.prefs.load()
                        s = fresh
                        syncNumberStrings(fresh)
                        saved = false
                        aiStatus = null
                        youStatus = null
                        mailStatus = null
                        erased = true
                    }
                }) { Text("بله، پاک کن", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text("انصراف") }
            }
        )
    }
}
