package com.moneyrecords.app.data.mapper

import com.moneyrecords.app.data.db.AccountEntity
import com.moneyrecords.app.data.db.AccountWithBalance
import com.moneyrecords.app.data.db.BudgetEntity
import com.moneyrecords.app.data.db.CategoryEntity
import com.moneyrecords.app.data.db.RecordEntity
import com.moneyrecords.app.data.db.RecordRow
import com.moneyrecords.app.data.db.RecurringRuleEntity
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecurringRule
import com.moneyrecords.app.domain.time.MonthRange
import java.time.Instant
import java.time.LocalDate

fun AccountWithBalance.toDomain(): Account = account.toDomain(balance)

fun AccountEntity.toDomain(balance: Long = openingBalance) = Account(
    id = id, name = name, type = type, openingBalance = openingBalance, iconKey = iconKey,
    archived = archived, sortOrder = sortOrder, balance = balance,
)

fun Account.toEntity(createdAt: Long) = AccountEntity(
    id = id, name = name.trim(), type = type, openingBalance = openingBalance, iconKey = iconKey,
    archived = archived, sortOrder = sortOrder, createdAt = createdAt,
)

fun CategoryEntity.toDomain() = Category(
    id = id, name = name, kind = kind, parentId = parentId, iconKey = iconKey,
    archived = archived, sortOrder = sortOrder,
)

fun Category.toEntity() = CategoryEntity(
    id = id, name = name.trim(), kind = kind, parentId = parentId, iconKey = iconKey,
    archived = archived, sortOrder = sortOrder,
)

/** Builds a two-level tree; orphans (parent missing or filtered out) are shown at the top level. */
fun List<CategoryEntity>.toTree(): List<Category> {
    val ids = mapTo(HashSet()) { it.id }
    val children = filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId }
    return filter { it.parentId == null || it.parentId !in ids }
        .map { parent -> parent.toDomain().copy(children = children[parent.id].orEmpty().map { it.toDomain() }) }
}

fun RecordEntity.toDomain() = Record(
    id = id, kind = kind, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, occurredAt = Instant.ofEpochMilli(occurredAt), note = note,
    recurringRuleId = recurringRuleId,
)

fun Record.toEntity(createdAt: Long, updatedAt: Long) = RecordEntity(
    id = id, kind = kind, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, occurredAt = occurredAt.toEpochMilli(), note = note.trim(),
    recurringRuleId = recurringRuleId, createdAt = createdAt, updatedAt = updatedAt,
)

fun RecordRow.toDomain() = RecordDetails(
    record = record.toDomain(),
    accountName = accountName,
    toAccountName = toAccountName,
    categoryName = categoryName,
    categoryIconKey = categoryIconKey,
)

fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = id, kind = kind, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, note = note, frequency = frequency, interval = interval,
    startDate = LocalDate.ofEpochDay(startDate), endDate = endDate?.let(LocalDate::ofEpochDay),
    nextRunDate = LocalDate.ofEpochDay(nextRunDate), active = active,
)

fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id, kind = kind, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, note = note.trim(), frequency = frequency, interval = interval,
    startDate = startDate.toEpochDay(), endDate = endDate?.toEpochDay(),
    nextRunDate = nextRunDate.toEpochDay(), active = active,
)

fun BudgetEntity.toDomain() = Budget(
    id = id, categoryId = categoryId, month = MonthRange.fromKey(yearMonth), limit = limitAmount, scope = scope,
)

fun Budget.toEntity() = BudgetEntity(
    id = id, scope = scope, categoryId = categoryId, yearMonth = MonthRange.key(month), limitAmount = limit,
)
