package ru.vizbash.paramail.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.paramail.ParamailApp
import ru.vizbash.paramail.R
import ru.vizbash.paramail.databinding.ActivityMainBinding
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.ui.messagelist.MessageListFragment

private const val ACCOUNT_GROUP = 1
private const val FOLDER_GROUP = 2

private val STANDARD_FOLDER_NAMES = mapOf(
    "INBOX" to R.string.inbox,
)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var ui: ActivityMainBinding
    private val model: MainViewModel by viewModels()

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    private var accountId = -1
    private var folderId = -1

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
            model.searchState.value = SearchState.Closed

            when (dest.id) {
                R.id.gettingStartedFragment -> {
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                }
                R.id.messageListFragment -> {
                    lifecycleScope.launch {
                        val init = (application as ParamailApp).getInitialFolder() ?: return@launch

                        model.updateFolderList(init.first)

                        this@MainActivity.accountId = args?.getInt(MessageListFragment.ARG_ACCOUNT_ID)
                            ?: init.first
                        val folderName = args?.getString(MessageListFragment.ARG_FOLDER_NAME)
                            ?: init.second
                        folderId = resolveFolderId(folderName)

                        val app = application as ParamailApp
                        app.saveLastFolder(this@MainActivity.accountId, folderName)

                        updateNavigationView()
                    }
                }
            }
        }

        checkNotifyPermission()

        initDrawerMenu()

        lifecycleScope.launch { observeMessageSend() }
    }

    private fun checkNotifyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {

            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        if (!newText.isNullOrEmpty()) {
                            model.searchState.value = SearchState.Searched(newText)
                        }
                        return true
                    }
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

    private fun resolveFolderId(folderName: String): Int {
        return model.folderList.value.find { it.name == folderName }!!.id
    }

    private fun resolveFolderName(folderId: Int): String {
        return model.folderList.value.find { it.id == folderId }!!.name
    }

    private fun navigateToFolder(accountId: Int, folderName: String) {
        val opts = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(null, inclusive = false, saveState = true)
            .build()

        val args = bundleOf(
            MessageListFragment.ARG_ACCOUNT_ID to accountId,
            MessageListFragment.ARG_FOLDER_NAME to folderName,
        )
        navController.navigate(R.id.messageListFragment, args, opts)
    }

    private fun updateNavigationView() {
        ui.navigationView.menu.forEach { item ->
            when (item.groupId) {
                ACCOUNT_GROUP -> item.isChecked = item.itemId == accountId
                FOLDER_GROUP -> item.isChecked = item.itemId == folderId
            }
        }
    }

    private fun initDrawerMenu() {
        val menu = ui.navigationView.menu

        ui.navigationView.setNavigationItemSelectedListener { item ->
            when {
                item.itemId == R.id.item_add_account -> {
                    navController.navigate(R.id.accountSetupWizardFragment)
                }
                item.itemId == R.id.item_settings -> {
                    navController.navigate(R.id.settingsFragment)
                }
                item.groupId == ACCOUNT_GROUP -> {
                    navigateToFolder(item.itemId, resolveFolderName(folderId))
                }
                item.groupId == FOLDER_GROUP -> {
                    navigateToFolder(accountId, resolveFolderName(item.itemId))
                }
                else -> {}
            }

            ui.root.close()
            false
        }

        menu.add(Menu.NONE, R.id.item_settings, 2001, R.string.settings).apply {
            setIcon(R.drawable.ic_settings)
        }

        lifecycleScope.launch { menuObserveAccountList(menu) }
        lifecycleScope.launch { menuObserveFolderList(menu) }
    }

    private suspend fun menuObserveAccountList(menu: Menu) {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.accountList.filter(List<MailAccount>::isNotEmpty).collect { accountList ->
                menu.removeGroup(ACCOUNT_GROUP)

                accountList.forEachIndexed { i, account ->
                    menu.add(
                        ACCOUNT_GROUP,
                        account.id,
                        i,
                        account.imap.creds!!.login,
                    ).apply {
                        setIcon(R.drawable.ic_email)
                        isChecked = account.id == accountId
                    }
                }
                menu.add(ACCOUNT_GROUP, R.id.item_add_account, Menu.NONE, R.string.add_account)
                    .apply {
                        setIcon(R.drawable.ic_plus)
                    }
            }
        }
    }

    private suspend fun menuObserveFolderList(menu: Menu) {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.folderList.filter(List<FolderEntity>::isNotEmpty).collect { folderList ->
                menu.removeGroup(FOLDER_GROUP)

                folderList.forEachIndexed { i, folder ->
                    val displayName = localizeFolderName(folder.name)

                    menu.add(FOLDER_GROUP, folder.id, 1000 + i, displayName).apply {
                        setIcon(R.drawable.ic_folder)
                        isChecked = folder.id == folderId
                    }
                }
            }
        }
    }

    private suspend fun observeMessageSend() {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.messageSendState.filterNotNull().collect {
                when (it) {
                    is MessageSendState.AboutToSend -> {
                        Snackbar.make(
                            ui.root,
                            getString(R.string.message_will_be_sent, it.delayMs / 1000),
                            it.delayMs.toInt(),
                        ).setAction(R.string.cancel) {
                            model.cancelMessageSend()
                        }.show()
                    }
                    MessageSendState.Sent -> {
                        Snackbar.make(ui.root, R.string.message_sent, Snackbar.LENGTH_SHORT).show()

                        getSharedPreferences(MessageComposerFragment.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                            .edit { clear() }
                    }
                    MessageSendState.Error -> {
                        Snackbar.make(ui.root, R.string.message_send_error, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}