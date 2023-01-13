package ru.vizbash.paramail.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import ru.vizbash.paramail.R

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}