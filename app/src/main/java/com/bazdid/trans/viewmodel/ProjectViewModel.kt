package com.bazdid.trans.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bazdid.trans.data.db.AppDatabase
import com.bazdid.trans.data.entity.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val projectDao = db.projectDao()

    val projects = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProject(name: String, description: String) {
        viewModelScope.launch {
            val project = Project(name = name, description = description)
            projectDao.insert(project)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectDao.delete(project)
        }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch {
            projectDao.update(project.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
