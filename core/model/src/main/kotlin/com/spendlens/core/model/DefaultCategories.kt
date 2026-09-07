package com.spendlens.core.model

/**
 * The categories a new install starts with.
 *
 * Production data, not fixtures — deliberately separate from `sample/SampleData.kt`, which exists
 * only for previews and tests. They share ids so that a preview and a real first run show the same
 * colours, but they are not the same thing and must not be merged: one ships, one does not.
 */
object DefaultCategories {
    val groceries = Category(id = "cat-groceries", name = "Groceries", colorIndex = 0, iconKey = "cart")
    val dining = Category(id = "cat-dining", name = "Dining Out", colorIndex = 1, iconKey = "restaurant")
    val transport = Category(id = "cat-transport", name = "Transport", colorIndex = 2, iconKey = "car")
    val utilities = Category(id = "cat-utilities", name = "Utilities", colorIndex = 3, iconKey = "bolt")
    val shopping = Category(id = "cat-shopping", name = "Shopping", colorIndex = 4, iconKey = "bag")
    val health = Category(id = "cat-health", name = "Health", colorIndex = 5, iconKey = "heart")

    val all: List<Category> = listOf(groceries, dining, transport, utilities, shopping, health)
}
