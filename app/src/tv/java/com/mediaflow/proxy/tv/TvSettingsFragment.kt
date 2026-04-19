package com.mediaflow.proxy.tv

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * Leanback settings host.  This is the root fragment the settings activity
 * inflates; it wraps the actual [RootPreferencesFragment] in the two-column
 * Leanback chrome (current screen on the left, nested screen on the right)
 * and routes preference-dialog fragments (EditTextPreference, ListPreference,
 * …) through the built-in dialog container — without this host, clicking
 * any dialog preference pops the whole activity.
 */
class TvSettingsFragment : LeanbackSettingsFragmentCompat() {

    override fun onPreferenceStartInitialScreen() {
        startPreferenceFragment(RootPreferencesFragment())
    }

    override fun onPreferenceStartFragment(
        caller: androidx.preference.PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragment = childFragmentManager.fragmentFactory
            .instantiate(requireActivity().classLoader, pref.fragment ?: return false)
        fragment.arguments = pref.extras
        fragment.setTargetFragment(caller, 0)
        startPreferenceFragment(fragment)
        return true
    }

    override fun onPreferenceStartScreen(
        caller: androidx.preference.PreferenceFragmentCompat,
        pref: PreferenceScreen
    ): Boolean {
        val fragment = RootPreferencesFragment()
        fragment.arguments = Bundle().apply {
            putString(ARG_PREFERENCE_ROOT, pref.key)
        }
        startPreferenceFragment(fragment)
        return true
    }

    /** Inner class so the root preferences fragment has access to the parent
     *  LeanbackSettingsFragmentCompat's fragment manager — required for
     *  dialog preferences to find their container. */
    class RootPreferencesFragment : LeanbackPreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = TvConfigDataStore(requireContext())
            setPreferencesFromResource(
                com.mediaflow.proxy.R.xml.tv_preferences,
                rootKey
            )
        }

        // Leanback's settings_preference_fragment layout reserves ~180 dp at
        // the top for a `decor_title` text view that this fragment never
        // populates — leaving a large empty band above the first preference.
        // Walk the view tree and collapse any decor_title_container to GONE.
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            hideDecorTitle(view)
        }

        private fun hideDecorTitle(root: View) {
            if (root is ViewGroup) {
                // Match by the `decor_title_container` id exposed in the
                // androidx.preference layout (kept stable across versions).
                val id = resources.getIdentifier(
                    "decor_title_container", "id", requireContext().packageName
                ).let { local ->
                    if (local != 0) local
                    else resources.getIdentifier(
                        "decor_title_container", "id", "android"
                    )
                }
                if (id != 0) {
                    root.findViewById<View>(id)?.visibility = View.GONE
                }
                // Fallback: iterate children in case the id lookup failed on
                // some OEM variant of the Leanback preference layout.
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    if (child.id != 0) {
                        try {
                            val name = resources.getResourceEntryName(child.id)
                            if (name == "decor_title_container") child.visibility = View.GONE
                        } catch (_: Exception) { /* no entry name */ }
                    }
                    if (child is ViewGroup) hideDecorTitle(child)
                }
            }
        }
    }

    companion object {
        private const val ARG_PREFERENCE_ROOT = "root"
    }
}
