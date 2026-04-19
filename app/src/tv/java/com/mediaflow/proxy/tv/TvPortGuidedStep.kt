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

/** Guided-step dialog for editing the listen port on Android TV. */
class TvPortGuidedStep : GuidedStepSupportFragment() {

    companion object {
        private const val ARG_CURRENT = "current_port"
        private const val ACTION_PORT = 1L

        fun create(currentPort: Int) = TvPortGuidedStep().apply {
            arguments = bundleOf(ARG_CURRENT to currentPort)
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Listen Port",
            "Port the proxy listens on (1–65535).",
            getString(com.mediaflow.proxy.R.string.app_name),
            null,
        )
    }

    override fun onCreateActions(
        actions: MutableList<GuidedAction>,
        savedInstanceState: Bundle?,
    ) {
        val current = requireArguments().getInt(ARG_CURRENT, 8888)
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_PORT)
                .title("Port")
                .description(current.toString())
                .descriptionEditable(true)
                .descriptionInputType(InputType.TYPE_CLASS_NUMBER)
                .build()
        )
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        if (action.id != ACTION_PORT) return GuidedAction.ACTION_ID_CURRENT
        val port = action.description?.toString()?.trim()?.toIntOrNull()
        if (port == null || port !in 1..65535) {
            // Re-enter edit mode — keeps focus on the input.
            return ACTION_PORT
        }
        val ctx = requireContext().applicationContext
        lifecycleScope.launch {
            val repo = ConfigRepository(ctx)
            val cfg = repo.config.first()
            repo.save(cfg.copy(port = port))
        }
        parentFragmentManager.popBackStack()
        return GuidedAction.ACTION_ID_FINISH
    }
}
