package com.spendlens.core.model

/**
 * One expense, changed on this device and on another before either saw the other's change.
 *
 * [local] is this device's version and may be a deletion. [remote] is the version the server holds.
 * The user keeps one; the other is discarded.
 */
data class ExpenseConflict(
    val local: Expense,
    val remote: Expense,
) {
    /**
     * What the two versions actually disagree on — the part the user is choosing between. A conflict
     * over one changed amount should not make them re-read six identical fields to find it.
     *
     * A deletion on one side disagrees with everything, so it is reported on its own: listing the
     * fields a deleted expense no longer "has" would only obscure the real question, keep it or not.
     */
    val differences: Set<ExpenseField>
        get() = if (local.isDeleted != remote.isDeleted) {
            setOf(ExpenseField.DELETED)
        } else {
            buildSet {
                if (local.merchant != remote.merchant) add(ExpenseField.MERCHANT)
                if (local.amountMinor != remote.amountMinor || local.currency != remote.currency) {
                    add(ExpenseField.AMOUNT)
                }
                if (local.occurredOn != remote.occurredOn) add(ExpenseField.DATE)
                if (local.categoryId != remote.categoryId) add(ExpenseField.CATEGORY)
                if (local.note != remote.note) add(ExpenseField.NOTE)
            }
        }
}

/** The parts of an expense a user can edit, and so the parts two edits can disagree on. */
enum class ExpenseField {
    MERCHANT,
    AMOUNT,
    DATE,
    CATEGORY,
    NOTE,
    DELETED,
}
