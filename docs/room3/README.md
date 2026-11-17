# Room3 in Rovia

Rovia persists its library catalog, user collections and playback records in a
single SQLite database through Room 3 (`androidx.room3`), version
**3.1.0-alpha03**. This folder documents which parts of Room3 the project
uses, how the database works, and which features depend on it.

- [Database mechanism](database-mechanism.md) — lifecycle, schema layout,
  identity and snapshot rules, read and write paths.
- [Features that require the database](features.md) — the feature-by-feature
  map of tables and access paths.

## Integration

| Aspect | Value |
| --- | --- |
| Dependency | `androidx.room3:room3-runtime` 3.1.0-alpha03 |
| Compiler | `androidx.room3:room3-compiler` 3.1.0-alpha03, applied through KSP |
| Gradle plugin | `androidx.room3`, with `room3 { schemaDirectory("$projectDir/schemas") }` |
| Database class | `RoviaDatabase` (`data/local/database/RoviaDatabase.kt`) |
| Database file | `rovia.db`, created by `RoviaDatabaseFactory` |
| Schema | Version 1, `exportSchema = true`; exported to `app/schemas/com.rovia.music.data.local.database.RoviaDatabase/1.json` |
| SQLite driver | `AndroidSQLiteDriver` (the framework SQLite driver) |
| Query context | `Dispatchers.IO`, set on the database builder |

## Room3 surface used

The data layer lives under `data/local/` (`entity/`, `dao/`, `database/`) and
uses only the following Room3 features:

- `@Database` with an explicit entity list, one version, and schema export.
- `@Entity` data classes with named tables, composite and single-column
  `@Index` declarations (including unique indexes), and `@ForeignKey`
  relations with `CASCADE` and `SET_NULL` delete behaviours.
- `@PrimaryKey(autoGenerate = true)` surrogate keys on every table except the
  cross-reference tables, the state tables, and `artwork_assets`, which keys
  on the natural `assetKey` string; the others key on their foreign or
  natural columns.
- `@Dao` interfaces — 16 of them, one per area — exposing:
  - `@Query` for all reads and for `UPDATE`/`DELETE` statements (136 queries
    in total), including joins across the catalog tables and small data-class
    row projections such as `PlaylistSummaryRow`.
  - `@Insert` with `OnConflictStrategy.IGNORE` for catalog and record rows,
    and `OnConflictStrategy.REPLACE` for singleton state rows.
  - `@Update` for targeted row updates.
  - `Flow`-returning queries for anything the UI observes, and `suspend`
    functions for one-shot reads and writes.
- `Room.databaseBuilder` with a driver and a query coroutine context; the
  factory registers no migrations and no destructive fallback.

Room3 features the project deliberately does not use: `@Delete` (deletes are
written as `@Query` statements), `@Upsert`, `@Transaction` and
`withTransaction` (multi-step writes are sequenced by repositories, and each
DAO call commits on its own), `@TypeConverters` (columns are primitives and
strings only), `@RawQuery`, and the Paging integration.

## Access rule

Screens and view models never call DAOs. All database access goes through the
repositories wired in `AppContainer` (`RoomLibraryRepository`,
`DatabaseSearchRepository`, `UserLibraryRepository`,
`PlaybackHistoryRepository`, `PlaybackStatisticsRepository`), through
`MediaStoreCatalogSynchronizer` for catalog writes, and through
`PlaybackService` for playback record writes.
