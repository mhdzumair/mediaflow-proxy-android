package com.mediaflow.proxy.tv

import android.os.Bundle
import android.text.InputType
import androidx.core.os.bundleOf
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.mediaflow.proxy.ConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Guided-step dialog for editing the API password on Android TV. */
class TvPasswordGuidedStep : GuidedStepSupportFragment() {

    companion object {
        private const val ARG_CURRENT = "current_password"
        private const val ACTION_PASSWORD = 2L

        fun create(currentPassword: String) = TvPasswordGuidedStep().apply {
            arguments = bundleOf(ARG_CURRENT to currentPassword)
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "API Password",
            "Password required to access the proxy's HTTP API.",
            getString(com.mediaflow.proxy.R.string.app_name),
            null,
        )
    }

    override fun onCreateActions(
        actions: MutableList<GuidedAction>,
        savedInstanceState: Bundle?,
    ) {
        val current = requireArguments().getString(ARG_CURRENT, "")
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_PASSWORD)
                .title("Password")
                .description(current)
                .descriptionEditable(true)
                // textVisiblePassword avoids the hidden-dots edit field (harder to
                // work with on a TV remote) while still disabling autocorrect.
                .descriptionInputType(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
                .build()
        )
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        if (action.id != ACTION_PASSWORD) return GuidedAction.ACTION_ID_CURRENT
        val password = action.description?.toString().orEmpty()
        val ctx = requireContext().applicationContext
        lifecycleScope.launch {
            val repo = ConfigRepository(ctx)
            val cfg = repo.config.first()
            repo.save(cfg.copy(apiPassword = password))
        }
        parentFragmentManager.popBackStack()
        return GuidedAction.ACTION_ID_FINISH
    }
}
