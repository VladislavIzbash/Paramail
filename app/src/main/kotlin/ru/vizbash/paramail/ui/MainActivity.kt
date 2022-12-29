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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.ActivityMainBinding
import ru.vizbash.paramail.ui.messagelist.MessageListFragment

private const val ACCOUNT_GROUP = 1
private const val FOLDER_GROUP = 2
private const val DEFAULT_FOLDER = "INBOX"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        const val LAST_ACCOUNT_ID_KEY = "last_account_id"
        const val LAST_FOLDER_KEY = "last_folder"
    }

    private lateinit var ui: ActivityMainBinding
    private val model: MainViewModel by viewModels()

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

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
        }

        initDrawer()
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

    private suspend fun observeAccountList() {
        val menu = ui.navigationView.menu

        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                model.accountList.collect { accountList ->
                    menu.removeGroup(ACCOUNT_GROUP)

                    for (account in accountList) {
                        menu.add(
                            ACCOUNT_GROUP,
                            account.id,
                            Menu.NONE,
                            account.imap.creds!!.login,
                        ).apply {
                            setIcon(R.drawable.ic_email)
                            isChecked = account.id == model.selectedAccountId.value
                        }
                    }
                    menu.add(ACCOUNT_GROUP, R.id.item_add_account, Menu.NONE, R.string.add_account)
                        .apply {
                            setIcon(R.drawable.ic_add)
                        }
                }
            }

            launch {
                model.selectedAccountId.filterNotNull().collect { accountId ->
                    menu.removeGroup(FOLDER_GROUP)

                    for (folder in model.getFolderList()) {
                        menu.add(FOLDER_GROUP, Menu.NONE, Menu.NONE, folder).apply {
                            setIcon(R.drawable.ic_folder)
                            isChecked = folder == DEFAULT_FOLDER
                        }
                    }

                    menu.forEach { item ->
                        if (item.groupId == ACCOUNT_GROUP) {
                            item.isChecked = item.itemId == accountId
                        }
                    }

                    getPreferences(Context.MODE_PRIVATE).edit {
                        putInt(LAST_ACCOUNT_ID_KEY, accountId)
                        remove(LAST_FOLDER_KEY)
                    }
                }
            }
        }
    }

    private fun initDrawer() {
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

            val selected = when {
                item.itemId == R.id.item_add_account -> {
                    navController.navigate(R.id.action_global_accountSetupWizardFragment)
                    false
                }
                item.groupId == ACCOUNT_GROUP -> {
                    val args = bundleOf(
                        MessageListFragment.ARG_ACCOUNT_ID to item.itemId,
                        MessageListFragment.ARG_FOLDER_NAME to DEFAULT_FOLDER,
                    )
                    navController.navigate(R.id.messageListFragment, args, opts)
                    model.selectedAccountId.value = item.itemId
                    true
                }
                item.groupId == FOLDER_GROUP -> {
                    val args = bundleOf(
                        MessageListFragment.ARG_ACCOUNT_ID to model.selectedAccountId.value,
                        MessageListFragment.ARG_FOLDER_NAME to item.title,
                    )
                    navController.navigate(R.id.messageListFragment, args, opts)

                    getPreferences(Context.MODE_PRIVATE).edit {
                        putString(LAST_FOLDER_KEY, item.title.toString())
                    }
                    true
                }
                else -> false
            }
            ui.root.close()
            selected
        }

        menu.add(Menu.NONE, Menu.NONE, 101, R.string.settings).apply {
            setIcon(R.drawable.ic_settings)
        }

        lifecycleScope.launch {
            observeAccountList()
        }
    }
}