package com.example.codsofttodo

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.codsofttodo.data.TimeState
import com.example.codsofttodo.data.ToDoEntry
import com.example.codsofttodo.data.TodoRepository
import com.example.codsofttodo.data.TodoRoomDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TodoViewModel(application: Application): ViewModel(){
    val todos: LiveData<List<ToDoEntry>>
    private val repository: TodoRepository
    private var _title by mutableStateOf("")
    var showListMenuOnHome by mutableStateOf(false)
    var showListMenuOnNewTask by mutableStateOf(false)
    var showAddListDialogueOnNewTask by mutableStateOf(false)
    var showAddListDialogueOnHome by mutableStateOf(false)
    var showMoreAction by mutableStateOf(false)
    var showRepeatMenu by mutableStateOf(false)
    var currentSharedContent by mutableStateOf("")
    var currentDeletedList by mutableStateOf("")

    var showSearchBar by  mutableStateOf(false)
    var groupedItem by  mutableStateOf<List<ToDoEntry>>(emptyList())
    var forFilter by mutableStateOf(false)
    var forShare by mutableStateOf(false)
    var forDeleteList by mutableStateOf(false)
    var forComplete by mutableStateOf(false)

    val onShowSearchBar = {showSearchBar = !showSearchBar}

    var currentToDoEntry by mutableStateOf(ToDoEntry(title= "", setDate = LocalDateTime.of(LocalDate.now(),LocalTime.now()),
        isDone = false, timeState = TimeState.NoDate, repeat = "", listType = ""))

    var forEdit by mutableStateOf(false)
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("TodoPrefs", Context.MODE_PRIVATE)

    private val _listType = MutableStateFlow(loadListType())
    val listType = _listType.asStateFlow()


    private fun saveListType(list: List<String>) {
        val editor = sharedPreferences.edit()
        editor.putString("listType", list.joinToString(","))
        editor.apply()
    }

    private fun loadListType(): MutableList<String> {
        val savedList = sharedPreferences.getString("listType", null)
        return savedList?.split(",")?.toMutableList() ?: mutableListOf("Default", "Completed")
    }
    fun onAddListMenu() {
        val currentList = _listType.value.toMutableList()
        currentList.add(list)
        _listType.value = currentList
        saveListType(currentList)
    }
    fun onRemoveListMenu() {
        val currentList = _listType.value.toMutableList()
        currentList.remove(currentDeletedList)
        _listType.value = currentList
        todos.value?.forEach { todo ->
            if (todo.listType == currentDeletedList) deleteTodo(todo)
        }
        saveListType(currentList)
    }


    private val _repeats =
        MutableStateFlow(mutableListOf("No repeat", "Once a Day",
            "Once a week", "Once a Month", "Once a Year"))

    val repeats: StateFlow<MutableList<String>>
        get() = _repeats.asStateFlow()

    private val _actions =
        MutableStateFlow(mutableListOf("Task Lists",
            "More Apps"))
    val actions: StateFlow<MutableList<String>>
        get() = _actions.asStateFlow()

    var selectedListTypeOnNewTask by mutableStateOf(_listType.value[0])
    var selectedListTypeOnHome by mutableStateOf("All Lists")
    var selectedRepeatType by mutableStateOf(_repeats.value[0])
    var selectedActionType by mutableStateOf(_actions.value[0])
    var _pickedDateTime by mutableStateOf<LocalDateTime?>(null)
    val pickedDateTime: LocalDateTime?
        get() = _pickedDateTime


    val onShowListMenuHome = {_: Boolean -> showListMenuOnHome = !showListMenuOnHome}
    val onShowListMenuNewTask = {_: Boolean -> showListMenuOnNewTask = !showListMenuOnNewTask}
    val onShowRepeatMenu = {_: Boolean -> showRepeatMenu = !showRepeatMenu}
    val onShowMoreAction = {_: Boolean -> showMoreAction = !showMoreAction}
    val onShowAddListDialogOnNewTask = {showAddListDialogueOnNewTask =!showAddListDialogueOnNewTask}
    val onShowAddListDialogOnHome = {showAddListDialogueOnHome =!showAddListDialogueOnHome}


    val title: String
        get() = _title
    val onTitleChange = { new: String -> _title = new }

    private var _list by mutableStateOf("")
    val list: String
        get() = _list
    val onListChange = { new: String -> _list = new }

    private var _search by mutableStateOf("")
    val search: String
        get() = _search
    val onSearchChange = { new: String -> _search = new }

    var formatDate by mutableStateOf("Date not set")
    var formatTime by mutableStateOf("Time not set")

    fun onCheck(check: Boolean) {
        repository.updateEntityField(
            entityId = currentToDoEntry.id,
            title = currentToDoEntry.title,
            setDate = currentToDoEntry.setDate,
            timeState = currentToDoEntry.timeState,
            repeat = currentToDoEntry.repeat,
            listType = currentToDoEntry.listType,
            isDone = check

        )
    }

    init {
        val todoDb = TodoRoomDatabase.getInstance(application)
        val todoDao = todoDb.databaseOperations()
        repository = TodoRepository(todoDao)
        todos = repository.allTodos
    }


    fun insertTodo() {
        val newToDoEntry = ToDoEntry(
            title = title,
            setDate = _pickedDateTime,
            isDone = false,
            repeat = selectedRepeatType,
            listType = selectedListTypeOnNewTask,
            timeState = timeState(_pickedDateTime?.toLocalDate())
            )
        repository.insertTodo(newToDoEntry)
        updateEntryFieldForAdd()
    }

    private fun timeState(date: LocalDate?): TimeState {
        val thisWeekEndDate = LocalDate.now().plusWeeks(1)
        val nextWeekStartDate = LocalDate.now().plusWeeks(1)
        val nextWeekEndDate = LocalDate.now().plusWeeks(2)

        return when {
            date == null -> TimeState.NoDate
            date.isEqual(LocalDate.now()) -> TimeState.Today
            date.isEqual(LocalDate.now().plusDays(1)) -> TimeState.Tomorrow
            date.isAfter(LocalDate.now()) && date.isBefore(thisWeekEndDate) -> TimeState.ThisWeek
            date.isAfter(nextWeekStartDate) && date.isBefore(nextWeekEndDate) -> TimeState.NextWeek
            date.isAfter(LocalDate.now()) && date.isBefore(LocalDate.now().plusMonths(1)) -> TimeState.NextMonth
            date.isAfter(LocalDate.now()) -> TimeState.Later
            else -> TimeState.Overdue
        }
    }



    fun deleteTodo(toDoEntry: ToDoEntry) {
        repository.deleteTodo(toDoEntry)
    }
    fun updateEntryFieldForEdit(toDoEntry: ToDoEntry) {
        _title = toDoEntry.title
        selectedRepeatType = toDoEntry.repeat
        _pickedDateTime = toDoEntry.setDate
        selectedListTypeOnNewTask = toDoEntry.listType
        formatDate = if (_pickedDateTime == null) "Date not set"
        else DateTimeFormatter.ofPattern("EEE, d MMM yyyy").format(_pickedDateTime)
        formatTime = if (_pickedDateTime == null) "Time not set"
        else DateTimeFormatter.ofPattern("h:mm a").format(_pickedDateTime)
    }


    fun updateEntryFieldForAdd() {
        _title = ""
        selectedRepeatType = repeats.value[0]
        _pickedDateTime = null
        selectedListTypeOnNewTask = listType.value[0]
        formatDate = if (_pickedDateTime == null) "Date not set"
        else DateTimeFormatter.ofPattern("EEE, d MMM yyyy").format(_pickedDateTime)
        formatTime = if (_pickedDateTime == null) "Time not set"
        else DateTimeFormatter.ofPattern("h:mm a").format(_pickedDateTime)
    }
    fun onEdit() {
        repository.updateEntityField(
            entityId = currentToDoEntry.id,
            title = title,
            setDate = _pickedDateTime,
            timeState= timeState(_pickedDateTime?.toLocalDate()),
            repeat = selectedRepeatType,
            listType = selectedListTypeOnNewTask,
            isDone = currentToDoEntry.isDone
        )
        updateEntryFieldForAdd()
    }
    fun searchByTitle(title: String): MutableLiveData<List<ToDoEntry>> {
        repository.searchByName(title)
        return repository.searchResults
    }

    fun searchByListType(listType: String): MutableLiveData<List<ToDoEntry>> {
        repository.getTodosByListType(listType)
        return repository.currentListTypeTodos
    }

    fun completedTodos(): MutableLiveData<List<ToDoEntry>> {
        repository.getCompletedTodos()
        return repository.completedTodos
    }
}