
package com.rovia.music.data.mediastore

import java.text.Normalizer
object ArtistCreditParser {

    private val protectedArtistPatterns = listOf(
        Regex(
            """Crosby,\s*Stills,\s*Nash\s*&\s*Young""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """Daryl\s+Hall\s*&\s*John\s+Oates""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """Earth,\s*Wind\s*&\s*Fire""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """Crosby,\s*Stills\s*&\s*Nash""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """Tyler,\s*The\s+Creator""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """Simon\s*&\s*Garfunkel""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """Hall\s*&\s*Oates""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """AC/DC""",
            RegexOption.IGNORE_CASE
        )
    )

    private val separatorPattern = Regex(
        """\s*(?:[,;|/&])\s*|\s+[-–—]\s+|\s+\b(?:feat(?:uring)?|ft)\.?\s+|\s+\bwith\b\s+|\s+[x×]\s+""",
        RegexOption.IGNORE_CASE
    )

    private val whitespacePattern = Regex("""\s+""")

    fun normalizeIdentity(value: String): String = Normalizer
        .normalize(whitespacePattern.replace(value.trim(), " "), Normalizer.Form.NFC)
        .lowercase()

    fun resolveDisplayNames(credits: Iterable<String?>): List<String> {
        val namesByIdentity = linkedMapOf<String, MutableList<String>>()
        credits.flatMap(::parse).forEach { name ->
            namesByIdentity.getOrPut(normalizeIdentity(name)) { mutableListOf() } += name
        }

        return namesByIdentity.values.map { variants ->
            variants.groupingBy { it }
                .eachCount()
                .maxWithOrNull(
                    compareBy<Map.Entry<String, Int>> { it.value }
                        .thenBy { it.key.count(Char::isUpperCase) }
                        .thenBy { it.key }
                )
                ?.key
                .orEmpty()
        }
    }

    fun resolvePrimaryDisplayName(credits: Iterable<String?>): String? {
        data class Candidate(
            val displayName: String,
            val occurrenceCount: Int,
            val firstOccurrence: Int,
        )

        val parsedNames = credits.flatMap(::parse)
        if (parsedNames.isEmpty()) return null

        val indexedNames = parsedNames.withIndex()
            .groupBy { normalizeIdentity(it.value) }
        val candidates = indexedNames.values.map { entries ->
            val variants = entries.groupingBy { it.value }.eachCount()
            val displayName = variants.maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.count(Char::isUpperCase) }
                    .thenBy { it.key }
            )?.key.orEmpty()
            Candidate(
                displayName = displayName,
                occurrenceCount = entries.size,
                firstOccurrence = entries.minOf { it.index },
            )
        }

        return candidates.sortedWith(
            compareByDescending<Candidate> { it.occurrenceCount }
                .thenBy { it.firstOccurrence }
                .thenByDescending { it.displayName.count(Char::isUpperCase) }
                .thenBy { it.displayName }
        ).firstOrNull()?.displayName
    }

    fun resolveDisplayName(
        artistName: String,
        primaryCredits: Iterable<String?>,
        fallbackCredits: Iterable<String?>,
    ): String? {
        val identity = normalizeIdentity(artistName)
        if (identity.isBlank()) return null

        val primaryCandidates = primaryCredits
            .flatMap(::parse)
            .filter { normalizeIdentity(it) == identity }
        val candidates = primaryCandidates.ifEmpty {
            fallbackCredits.flatMap(::parse)
                .filter { normalizeIdentity(it) == identity }
        }
        if (candidates.isEmpty()) return null

        return candidates
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.count(Char::isUpperCase) }
                    .thenBy { it.key }
            )
            ?.key
    }

    fun parse(artistCredit: String?): List<String> {
        val credit = artistCredit?.trim().orEmpty()

        if (credit.isEmpty()) {
            return emptyList()
        }

        val protectedCredit = protectKnownArtistNames(credit)
        val artists = separatorPattern.split(protectedCredit.value)
        val uniqueArtists = LinkedHashMap<String, String>()

        artists.forEach { artist ->
            val restoredArtist = restoreKnownArtistNames(
                artist.trim(),
                protectedCredit.names
            )

            val displayName = whitespacePattern
                .replace(restoredArtist, " ")
                .trim()

            if (displayName.isEmpty()) {
                return@forEach
            }

            val identity = normalizeIdentity(displayName)

            uniqueArtists.putIfAbsent(identity, displayName)
        }

        return uniqueArtists.values.toList()
    }

    private fun protectKnownArtistNames(
        credit: String
    ): ProtectedCredit {
        var protectedValue = credit
        val protectedNames = linkedMapOf<String, String>()

        protectedArtistPatterns.forEach { pattern ->
            protectedValue = pattern.replace(protectedValue) { match ->
                val token = "\u0001ROVIA_ARTIST_${protectedNames.size}\u0002"
                protectedNames[token] = match.value
                token
            }
        }

        return ProtectedCredit(
            value = protectedValue,
            names = protectedNames
        )
    }

    private fun restoreKnownArtistNames(
        artist: String,
        protectedNames: Map<String, String>
    ): String {
        var restoredArtist = artist

        protectedNames.forEach { (token, originalName) ->
            restoredArtist = restoredArtist.replace(token, originalName)
        }

        return restoredArtist
    }

    private data class ProtectedCredit(
        val value: String,
        val names: Map<String, String>
    )
}
