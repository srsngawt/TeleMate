package com.m3u.tv

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Channel
import java.util.Locale

@Immutable
data class ChannelVariant(
    val channel: Channel,
    val language: String,
    val quality: String
)

@Immutable
data class UnifiedChannel(
    val id: Int,
    val baseName: String,
    val category: String,
    val cover: String?,
    val isFavorite: Boolean,
    val variants: List<ChannelVariant>,
    val defaultLanguage: String,
    val defaultQuality: String
) {
    // Distinct languages available for this channel (normalized title case, no blanks or junk)
    val languages: List<String> = variants
        .map { TiviMateChannelParser.normalizeLanguage(it.language) }
        .filter { it.isNotBlank() }
        .distinct()

    // Qualities available across all variants
    val qualities: List<String> = variants.map { it.quality }.distinct()

    val hasMultipleLanguages: Boolean = languages.size > 1
    val hasMultipleQualities: Boolean = qualities.size > 1

    /**
     * Returns ONLY the qualities that actually exist for the specified language.
     * If a channel has only 2 qualities, this returns ONLY those 2 qualities.
     */
    fun availableQualitiesFor(language: String): List<String> {
        val norm = TiviMateChannelParser.normalizeLanguage(language)
        val matching = variants.filter { 
            val vLang = TiviMateChannelParser.normalizeLanguage(it.language)
            vLang.equals(norm, ignoreCase = true)
        }
        val quals = matching.map { it.quality }.distinct()
        return if (quals.isNotEmpty()) quals else qualities
    }

    /**
     * Gets the exact channel stream for the selected language and quality.
     */
    fun getStreamFor(language: String, quality: String): Channel {
        val norm = TiviMateChannelParser.normalizeLanguage(language)
        // 1. Exact match: language + quality
        val exact = variants.find {
            val vLang = TiviMateChannelParser.normalizeLanguage(it.language)
            vLang.equals(norm, ignoreCase = true) &&
            it.quality.equals(quality, ignoreCase = true)
        }
        if (exact != null) return exact.channel

        // 2. Best quality match for requested language
        if (norm.isNotBlank()) {
            val langMatches = variants.filter {
                val vLang = TiviMateChannelParser.normalizeLanguage(it.language)
                vLang.equals(norm, ignoreCase = true)
            }
            if (langMatches.isNotEmpty()) {
                return langMatches.first().channel
            }
        }

        // 3. Fallback to default/first variant
        return variants.first().channel
    }
}

object TiviMateChannelParser {
    private val qualityRegex = Regex(
        "(?i)\\b(4K|UHD|1080p|1080i|1080|FHD|720p|720|HD|576p|480p|SD|HEVC|H\\.?265)\\b"
    )

    private val languageRegex = Regex(
        "(?i)\\b(Hindi|English|Tamil|Telugu|Kannada|Malayalam|Bengali|Marathi|Punjabi|Gujarati|Urdu|Odia|Bhojpuri|Assamese|HIN|ENG|TAM|TEL|KAN|MAL|BEN|MAR|PUN|GUJ|URD)\\b"
    )

    private val bracketRegex = Regex("[\\[(]([^\\])]*)[\\])]")
    private val emptyBracketRegex = Regex("[\\[(]\\s*[\\])]")
    private val orphanBracketRegex = Regex("[\\[\\]()]")
    private val punctuationRegex = Regex("[-|_|:|/]+")
    private val spacesRegex = Regex("\\s+")

    private val parseCache = java.util.concurrent.ConcurrentHashMap<String, Triple<String, String, String>>(2048)

    fun parseChannel(channel: Channel): Triple<String, String, String> {
        val cacheKey = "${channel.category}:::${channel.title}"
        parseCache[cacheKey]?.let { return it }

        val rawTitle = channel.title.trim()
        var cleanTitle = rawTitle

        var detectedQuality = "1080p FHD"
        qualityRegex.find(rawTitle)?.let { match ->
            val q = match.value.uppercase(Locale.ROOT)
            detectedQuality = when {
                q.contains("4K") || q.contains("UHD") -> "4K UHD"
                q.contains("1080") || q.contains("FHD") -> "1080p FHD"
                q.contains("720") -> "720p HD"
                q.contains("HD") -> "1080p FHD"
                q.contains("576") || q.contains("480") || q.contains("SD") -> "480p SD"
                else -> "1080p FHD"
            }
            cleanTitle = cleanTitle.replace(match.value, "", ignoreCase = true)
        }

        var detectedLanguage: String? = null
        languageRegex.find(rawTitle)?.let { match ->
            detectedLanguage = normalizeLanguage(match.value)
            cleanTitle = cleanTitle.replace(match.value, "", ignoreCase = true)
        } ?: run {
            bracketRegex.find(rawTitle)?.let { match ->
                val inner = match.groupValues[1].trim()
                languageRegex.find(inner)?.let { langMatch ->
                    detectedLanguage = normalizeLanguage(langMatch.value)
                }
            }
        }

        // If title didn't specify language, check if category specifies it
        if (detectedLanguage == null) {
            languageRegex.find(channel.category)?.let { catLang ->
                detectedLanguage = normalizeLanguage(catLang.value)
            }
        }

        val finalLanguage = detectedLanguage ?: ""

        cleanTitle = cleanTitle
            .replace(emptyBracketRegex, "")
            .replace(bracketRegex, "")
            .replace(orphanBracketRegex, "")
            .replace(punctuationRegex, " ")
            .replace(spacesRegex, " ")
            .trim()

        if (cleanTitle.isEmpty()) {
            cleanTitle = rawTitle
        }

        val result = Triple(cleanTitle, finalLanguage, detectedQuality)
        parseCache[cacheKey] = result
        return result
    }

    fun normalizeLanguage(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() ||
            trimmed.startsWith("_") ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("no", ignoreCase = true) ||
            trimmed.equals("none", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true) ||
            trimmed.equals("und", ignoreCase = true)
        ) {
            return ""
        }
        return when (trimmed.uppercase(Locale.ROOT)) {
            "HI", "HIN", "HINDI" -> "Hindi"
            "EN", "ENG", "ENGLISH" -> "English"
            "TA", "TAM", "TAMIL" -> "Tamil"
            "TE", "TEL", "TELUGU" -> "Telugu"
            "KN", "KAN", "KANNADA" -> "Kannada"
            "ML", "MAL", "MALAYALAM" -> "Malayalam"
            "BN", "BEN", "BENGALI" -> "Bengali"
            "MR", "MAR", "MARATHI" -> "Marathi"
            "PA", "PUN", "PUNJABI" -> "Punjabi"
            "GU", "GUJ", "GUJARATI" -> "Gujarati"
            "UR", "URD", "URDU" -> "Urdu"
            "OR", "ODI", "ODIA", "ORIYA" -> "Odia"
            "BHO", "BHOJPURI" -> "Bhojpuri"
            "AS", "ASM", "ASSAMESE" -> "Assamese"
            "SAN", "SKT", "SANSKRIT" -> "Sanskrit"
            "FR", "FRA", "FRENCH" -> "French"
            "ES", "SPA", "SPANISH" -> "Spanish"
            "DE", "DEU", "GER", "GERMAN" -> "German"
            "AR", "ARA", "ARABIC" -> "Arabic"
            "RU", "RUS", "RUSSIAN" -> "Russian"
            "ZH", "ZHO", "CHI", "CHINESE" -> "Chinese"
            "JA", "JPN", "JAPANESE" -> "Japanese"
            "KO", "KOR", "KOREAN" -> "Korean"
            else -> trimmed.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    @Volatile
    private var lastRawChannelsRef: List<Channel>? = null
    @Volatile
    private var lastGroupedList: List<UnifiedChannel>? = null

    fun groupChannels(
        channels: List<Channel>,
        favoriteIds: Set<Int>
    ): List<UnifiedChannel> {
        val cached = lastGroupedList
        if (cached != null && lastRawChannelsRef === channels) {
            return cached.map { uc ->
                val isFav = uc.variants.any { it.channel.id in favoriteIds }
                if (uc.isFavorite == isFav) uc else uc.copy(isFavorite = isFav)
            }
        }

        val grouped = LinkedHashMap<String, MutableList<Triple<Channel, String, String>>>()

        for (ch in channels) {
            val (baseName, lang, qual) = parseChannel(ch)
            val key = "${ch.category.lowercase(Locale.ROOT)}::${baseName.lowercase(Locale.ROOT)}"
            grouped.getOrPut(key) { mutableListOf() }.add(Triple(ch, lang, qual))
        }

        val result = grouped.values.map { list ->
            val first = list.first().first
            val (baseName, _, _) = parseChannel(first)

            // Step 1: Detect explicit regional languages in this group
            val explicitLangs = list.map { it.second }.filter { it.isNotBlank() }.distinct()

            // Step 2: In Indian television networks (like Aastha, Colors, Zee News, Star Sports 2),
            // when regional language variants exist (Tamil, Telugu, Kannada, Gujarati, Marathi, etc.),
            // any variant in the group with no explicit language tag (e.g. "Aastha HD", "Colors", "Zee News")
            // is the primary national HINDI broadcast feed.
            val hasRegionalVariants = explicitLangs.any {
                it.equals("Tamil", ignoreCase = true) ||
                it.equals("Telugu", ignoreCase = true) ||
                it.equals("Kannada", ignoreCase = true) ||
                it.equals("Malayalam", ignoreCase = true) ||
                it.equals("Gujarati", ignoreCase = true) ||
                it.equals("Bengali", ignoreCase = true) ||
                it.equals("Marathi", ignoreCase = true) ||
                it.equals("Punjabi", ignoreCase = true) ||
                it.equals("Odia", ignoreCase = true) ||
                it.equals("Assamese", ignoreCase = true)
            }

            val variants = list.map { (channel, rawLang, qual) ->
                val resolvedLang = when {
                    rawLang.isNotBlank() -> rawLang
                    hasRegionalVariants -> "Hindi"
                    list.size > 1 && explicitLangs.isNotEmpty() -> "Hindi"
                    else -> rawLang
                }
                ChannelVariant(channel, resolvedLang, qual)
            }
            val isFav = variants.any { it.channel.id in favoriteIds }

            val allLangs = variants.map { it.language }
                .filter { it.isNotBlank() && !it.startsWith("_") && !it.equals("unknown", ignoreCase = true) }
                .distinct()
            // Strict Rule: If Hindi is actually present in the variants, prefer Hindi.
            // Otherwise use the first detected language. Never hardcode "Hindi" when absent.
            val defaultLang = when {
                allLangs.any { it.equals("Hindi", ignoreCase = true) } -> "Hindi"
                allLangs.isNotEmpty() -> allLangs.first()
                else -> ""
            }

            val qualRank = mapOf("4K UHD" to 4, "1080p FHD" to 3, "720p HD" to 2, "480p SD" to 1)
            val matchingQuals = variants.filter { it.language.equals(defaultLang, ignoreCase = true) }
                .map { it.quality }.distinct()
            val defaultQual = matchingQuals.maxByOrNull { qualRank[it] ?: 0 } ?: variants.first().quality

            UnifiedChannel(
                id = first.id,
                baseName = baseName,
                category = first.category,
                cover = variants.firstNotNullOfOrNull { it.channel.cover },
                isFavorite = isFav,
                variants = variants,
                defaultLanguage = defaultLang,
                defaultQuality = defaultQual
            )
        }
        lastRawChannelsRef = channels
        lastGroupedList = result
        return result
    }
}
