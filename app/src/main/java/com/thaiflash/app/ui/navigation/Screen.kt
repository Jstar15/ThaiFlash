package com.thaiflash.app.ui.navigation

import com.thaiflash.app.data.model.ReviewFocus
import com.thaiflash.app.data.model.SetStudyOrder
import com.thaiflash.app.data.model.StudyDirection

sealed class Screen(val route: String, val title: String) {
    data object Sets : Screen("sets", "Flashcards")
    data object Review : Screen("review", "Review")
    data object Search : Screen("search", "Search")
    data object Settings : Screen("settings", "Settings")

    data object SetDetail : Screen("set/{setId}", "Set") {
        const val ARG_SET_ID = "setId"
        fun routeFor(setId: String) = "set/$setId"
    }

    data object StudySet : Screen("study_set/{setId}/{order}", "Study") {
        const val ARG_SET_ID = "setId"
        const val ARG_ORDER = "order"
        fun routeFor(setId: String, order: SetStudyOrder = SetStudyOrder.IN_ORDER) =
            "study_set/$setId/${order.name}"
    }

    data object ListenSet : Screen("listen_set/{setId}/{order}", "Listen") {
        const val ARG_SET_ID = "setId"
        const val ARG_ORDER = "order"
        fun routeFor(setId: String, order: SetStudyOrder = SetStudyOrder.IN_ORDER) =
            "listen_set/$setId/${order.name}"
    }

    data object ReviewSession : Screen("review_session/{focus}/{direction}", "Review") {
        const val ARG_FOCUS = "focus"
        const val ARG_DIRECTION = "direction"
        fun routeFor(focus: ReviewFocus, direction: StudyDirection) =
            "review_session/${focus.name}/${direction.name}"
    }
}
