
package com.rovia.music.data.mediastore

import java.text.Normalizer
import java.util.Locale

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

            val identity = Normalizer
                .normalize(displayName, Normalizer.Form.NFC)
                .lowercase(Locale.ROOT)

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
