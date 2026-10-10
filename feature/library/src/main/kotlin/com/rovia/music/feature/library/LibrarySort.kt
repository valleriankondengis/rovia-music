
package com.rovia.music.feature.library

import com.rovia.music.core.model.Track

enum class LibrarySortOption {
    DEFAULT,
    TITLE,
    ARTIST,
    ALBUM,
    GENRE,
    DATE_ADDED,
    DATE_MODIFIED,
}

enum class LibrarySortOrder {
    ASCENDING,
    DESCENDING,
}

fun List<Track>.sortedForLibrary(
    option: LibrarySortOption,
    order: LibrarySortOrder,
): List<Track> {
    if (option == LibrarySortOption.DEFAULT) {
        return this
    }

    val comparator =
        Comparator<Track> { first, second ->
            val primaryResult =
                when (option) {
                    LibrarySortOption.DEFAULT ->
                        0

                    LibrarySortOption.TITLE ->
                        compareOrderedStrings(
                            first = first.title,
                            second = second.title,
                            order = order,
                        )

                    LibrarySortOption.ARTIST ->
                        compareNullableStrings(
                            first = first.artist,
                            second = second.artist,
                            order = order,
                        )

                    LibrarySortOption.ALBUM ->
                        compareNullableStrings(
                            first = first.album,
                            second = second.album,
                            order = order,
                        )

                    LibrarySortOption.GENRE ->
                        compareNullableStrings(
                            first = first.genre,
                            second = second.genre,
                            order = order,
                        )

                    LibrarySortOption.DATE_ADDED ->
                        compareOrderedLongs(
                            first = first.dateAddedEpochSeconds,
                            second = second.dateAddedEpochSeconds,
                            order = order,
                        )

                    LibrarySortOption.DATE_MODIFIED ->
                        compareNullableLongs(
                            first = first.dateModifiedEpochSeconds,
                            second = second.dateModifiedEpochSeconds,
                            order = order,
                        )
                }

            if (primaryResult != 0) {
                return@Comparator primaryResult
            }

            compareStrings(
                first = first.title,
                second = second.title,
            )
        }

    return sortedWith(comparator)
}

private fun compareOrderedStrings(
    first: String,
    second: String,
    order: LibrarySortOrder,
): Int {
    val result =
        compareStrings(
            first = first,
            second = second,
        )

    return applySortOrder(
        result = result,
        order = order,
    )
}

private fun compareNullableStrings(
    first: String?,
    second: String?,
    order: LibrarySortOrder,
): Int {
    if (first == null && second == null) {
        return 0
    }

    if (first == null) {
        return 1
    }

    if (second == null) {
        return -1
    }

    val result =
        compareStrings(
            first = first,
            second = second,
        )

    return applySortOrder(
        result = result,
        order = order,
    )
}

private fun compareOrderedLongs(
    first: Long,
    second: Long,
    order: LibrarySortOrder,
): Int {
    val result =
        first.compareTo(second)

    return applySortOrder(
        result = result,
        order = order,
    )
}

private fun compareNullableLongs(
    first: Long?,
    second: Long?,
    order: LibrarySortOrder,
): Int {
    if (first == null && second == null) {
        return 0
    }

    if (first == null) {
        return 1
    }

    if (second == null) {
        return -1
    }

    val result =
        first.compareTo(second)

    return applySortOrder(
        result = result,
        order = order,
    )
}

private fun applySortOrder(
    result: Int,
    order: LibrarySortOrder,
): Int {
    return when (order) {
        LibrarySortOrder.ASCENDING ->
            result

        LibrarySortOrder.DESCENDING ->
            -result
    }
}

private fun compareStrings(
    first: String,
    second: String,
): Int {
    return first.compareTo(
        other = second,
        ignoreCase = true,
    )
}
