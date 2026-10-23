package dev.wick.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.wick.data.entity.*
import dev.wick.data.structured.*
import dev.wick.features.items.moments.MomentAutomationOverrides
import dev.wick.features.items.moments.MomentAutomationRuleSet
import dev.wick.features.items.moments.StoredMomentAutomationConfig

/**
 * Persistence for the remaining automation domains.
 *
 * The red-packet and auto-transfer rule tables still exist in the schema (so an older build can
 * still read its data and a downgrade loses nothing), but no runtime code reads or writes them
 * any more: those features were removed from this build. Only the moment-automation half of
 * this DAO is live.
 */
@Dao
abstract class AutomationDao {
    @Query("SELECT * FROM moment_automation_rules WHERE featureKind = :featureKind ORDER BY featureKind, scopeType, ownerId")
    abstract suspend fun readMomentAutomationRules(featureKind: String): List<MomentAutomationRuleEntity>

    @Upsert
    abstract suspend fun upsert(row: MomentAutomationRuleEntity)

    @Query("DELETE FROM moment_automation_rules WHERE featureKind = :featureKind AND scopeType = :scopeType AND ownerId = :ownerId")
    abstract suspend fun deleteMomentAutomationRule(featureKind: String, scopeType: String, ownerId: String)

    @Query("SELECT * FROM moment_automation_keywords WHERE featureKind = :featureKind ORDER BY featureKind, scopeType, ownerId, position")
    abstract suspend fun readMomentAutomationKeywords(featureKind: String): List<MomentAutomationKeywordEntity>

    @Insert
    abstract suspend fun insertMomentAutomationKeywords(rows: List<MomentAutomationKeywordEntity>)

    @Query("DELETE FROM moment_automation_keywords WHERE featureKind = :featureKind AND scopeType = :scopeType AND ownerId = :ownerId")
    abstract suspend fun deleteMomentAutomationKeywords(featureKind: String, scopeType: String, ownerId: String)

    @Query("SELECT * FROM moment_automation_content_types WHERE featureKind = :featureKind ORDER BY featureKind, scopeType, ownerId, typeId")
    abstract suspend fun readMomentAutomationContentTypes(featureKind: String): List<MomentAutomationContentTypeEntity>

    @Insert
    abstract suspend fun insertMomentAutomationContentTypes(rows: List<MomentAutomationContentTypeEntity>)

    @Query("DELETE FROM moment_automation_content_types WHERE featureKind = :featureKind AND scopeType = :scopeType AND ownerId = :ownerId")
    abstract suspend fun deleteMomentAutomationContentTypes(featureKind: String, scopeType: String, ownerId: String)

    @Transaction
    open suspend fun getMomentAutomationConfig(featureKind: String): StoredMomentAutomationConfig {
        val keywords = readMomentAutomationKeywords(featureKind).groupBy { it.scopeType to it.ownerId }
        val types = readMomentAutomationContentTypes(featureKind).groupBy { it.scopeType to it.ownerId }
        val contacts = linkedMapOf<String, MomentAutomationOverrides>()
        var global: MomentAutomationRuleSet? = null
        readMomentAutomationRules(featureKind).forEach { row ->
            val key = row.scopeType to row.ownerId
            val value = row.toOverrides(keywords[key].orEmpty(), types[key].orEmpty())
            when (row.scopeType) {
                "GLOBAL" -> global = value.asGlobalRules()
                "CONTACT" -> contacts[row.ownerId] = value
            }
        }
        return StoredMomentAutomationConfig(
            global = requireNotNull(global) { "Global moment automation rule is missing" },
            contacts = contacts,
        )
    }

    @Transaction
    open suspend fun insertMomentAutomationConfig(featureKind: String, value: StoredMomentAutomationConfig) {
        saveMomentAutomationRule(featureKind, "GLOBAL", "", value.global.asOverrides())
        value.contacts.forEach { (id, rules) -> saveMomentAutomationRule(featureKind, "CONTACT", id, rules) }
    }

    @Transaction
    open suspend fun putMomentAutomationRule(
        featureKind: String,
        scopeType: String,
        ownerId: String,
        rules: MomentAutomationOverrides,
    ) {
        validateMomentScope(featureKind, scopeType, ownerId)
        // REPOST hides action in the editor, but historical action overrides remain data.
        if (scopeType != "GLOBAL" && rules.isEmpty(includeAction = true)) {
            deleteMomentAutomationRule(featureKind, scopeType, ownerId)
        } else {
            saveMomentAutomationRule(featureKind, scopeType, ownerId, rules)
        }
    }

    private suspend fun saveMomentAutomationRule(
        featureKind: String,
        scopeType: String,
        ownerId: String,
        rules: MomentAutomationOverrides,
    ) {
        upsert(rules.toEntity(featureKind, scopeType, ownerId))
        deleteMomentAutomationKeywords(featureKind, scopeType, ownerId)
        insertMomentAutomationKeywords(rules.keyword?.strings.orEmpty().mapIndexed { index, text ->
            MomentAutomationKeywordEntity(featureKind, scopeType, ownerId, index, text)
        })
        deleteMomentAutomationContentTypes(featureKind, scopeType, ownerId)
        insertMomentAutomationContentTypes(rules.contentType?.typeIds.orEmpty().map {
            MomentAutomationContentTypeEntity(featureKind, scopeType, ownerId, it)
        })
    }
}
