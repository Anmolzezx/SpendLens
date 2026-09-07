package com.spendlens.core.data.repository

import com.spendlens.core.database.dao.CategoryDao
import com.spendlens.core.database.entity.asDomainModel
import com.spendlens.core.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
}

class OfflineFirstCategoryRepository
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
    ) : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> =
            categoryDao.observeCategories().map { entities -> entities.map { it.asDomainModel() } }
    }
