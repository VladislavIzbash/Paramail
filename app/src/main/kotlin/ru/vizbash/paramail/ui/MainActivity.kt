package ru.vizbash.paramail.ui

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.ActivityMainBinding
import ru.vizbash.paramail.ui.messagelist.MessageListFragment

private const val ACCOUNT_GROUP = 1
private const val FOLDER_GROUP = 2

private val STANDARD_FOLDER_NAMES = mapOf(
    "INBOX" to R.string.inbox,
)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAST_ACCOUNT_ID = "last_account_id"
        const val KEY_LAST_FOLDER_NAME = "last_folder_name"

        const val DEFAULT_FOLDER = "INBOX"
    }

    private lateinit var ui: ActivityMainBinding
    private val model: MainViewModel by viewModels()

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    private val prefs by lazy { getPreferences(Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        setSupportActionBar(ui.toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            as NavHostFragment
        navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(setOf(R.id.messageListFragment), ui.root)
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, dest, args ->
            invalidateOptionsMenu()

            if (dest.id == R.id.messageListFragment) {
                val accountId = args!!.getInt(MessageListFragment.ARG_ACCOUNT_ID)
                val folderName = args.getString(MessageListFragment.ARG_FOLDER_NAME)!!

                prefs.edit {
                    putInt(KEY_LAST_ACCOUNT_ID, accountId)
                    putString(KEY_LAST_FOLDER_NAME, folderName)
                }

                model.switchAccount(accountId)
                menuSwitchFolder(ui.navigationView.menu, accountId, folderName)
            }
        }

        drawerInit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return when (navController.currentDestination?.id) {
            R.id.messageListFragment -> {
                menuInflater.inflate(R.menu.message_list, menu)

                val searchItem = menu.findItem(R.id.item_search)
                val searchView = searchItem.actionView!! as SearchView

                searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                        model.searchState.value = SearchState.Opened
                        return true
                    }

                    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                        model.searchState.value = SearchState.Closed
                        return true
                    }
                })
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        model.searchState.value = SearchState.Searched(query!!)
                        return true
                    }

                    override fun onQueryTextChange(newText: String?) = true
                })

                true
            }
            else -> false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun localizeFolderName(name: String): String {
        return STANDARD_FOLDER_NAMES[name]?.let { getString(it) } ?: name
    }

    fun setFolderName(name: String) {
        supportActionBar?.title = localizeFolderName(name)
    }

    private fun drawerInit() {
        val menu = ui.navigationView.menu

        ui.navigationView.setNavigationItemSelectedListener { item ->
            val opts = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(
                    navController.graph.findStartDestination().id,
                    inclusive = false,
                    saveState = true,
                )
                .build()

             when {
                item.itemId == R.id.item_add_account -> {
                    navController.navigate(R.id.action_global_accountSetupWizardFragment)
                }
                item.groupId == ACCOUNT_GROUP -> {
                    val args = bundleOf(
                        MessageListFragment.ARG_ACCOUNT_ID to item.itemId,
                        MessageListFragment.ARG_FOLDER_NAME to DEFAULT_FOLDER,
                    )
                    navController.navigate(R.id.messageListFragment, args, opts)
                }
                item.groupId == FOLDER_GROUP -> {
                    val folder = model.folderList.value.find { it.id == item.itemId }!!
                    val args = bundleOf(
                        MessageListFragment.ARG_ACCOUNT_ID to prefs.getInt(KEY_LAST_ACCOUNT_ID, -1),
                        MessageListFragment.ARG_FOLDER_NAME to folder.name,
                    )
                    navController.navigate(R.id.messageListFragment, args, opts)
                }
                else -> {}
            }

            ui.root.close()
            false
        }

        menu.add(Menu.NONE, Menu.NONE, 2001, R.string.settings).apply {
            setIcon(R.drawable.ic_settings)
        }

        lifecycleScope.launch { menuObserveAccountList(menu) }
        lifecycleScope.launch { menuObserveFolderList(menu) }
    }

    private fun menuSwitchFolder(menu: Menu, accountId: Int, folderName: String) {
        var accountChanged = true
        menu.forEach { item ->
            if (item.groupId == ACCOUNT_GROUP) {
                if (item.itemId == accountId) {
                    accountChanged = false
                }

                item.isChecked = item.itemId == accountId
            }
        }

        if (!accountChanged) {
            val lastFolder = model.folderList.value.find { it.name == folderName}!!

            menu.forEach { item ->
                if (item.groupId == FOLDER_GROUP) {
                    item.isChecked = item.itemId == lastFolder.id
                }
            }
        }
    }

    private suspend fun menuObserveFolderList(menu: Menu) {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.folderList.collect { folderList ->
                menu.removeGroup(FOLDER_GROUP)

                val lastFolderName = prefs.getString(KEY_LAST_FOLDER_NAME, DEFAULT_FOLDER)
                val lastFolder = model.folderList.value.find { it.name == lastFolderName} ?: return@collect

                folderList.forEachIndexed { i, folder ->
                    val displayName = localizeFolderName(folder.name)

                    menu.add(FOLDER_GROUP, folder.id, 1000 + i, displayName).apply {
                        setIcon(R.drawable.ic_folder)
                        isChecked = folder.id == lastFolder.id
                    }
                }
            }
        }
    }

    private suspend fun menuObserveAccountList(menu: Menu) {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.accountList.collect { accountList ->
                val lastAccountId = prefs.getInt(KEY_LAST_ACCOUNT_ID, -1)

                menu.removeGroup(ACCOUNT_GROUP)

                accountList.forEachIndexed { i, account ->
                    menu.add(
                        ACCOUNT_GROUP,
                        account.id,
                        i,
                        account.imap.creds!!.login,
                    ).apply {
                        setIcon(R.drawable.ic_email)
                        isChecked = account.id == lastAccountId
                    }
                }
                menu.add(ACCOUNT_GROUP, R.id.item_add_account, Menu.NONE, R.string.add_account)
                    .apply {
                        setIcon(R.drawable.ic_add)
                    }
            }
        }
    }
}