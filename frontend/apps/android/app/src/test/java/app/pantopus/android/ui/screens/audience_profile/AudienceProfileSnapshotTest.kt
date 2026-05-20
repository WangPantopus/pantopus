@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.audience_profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshots for T3.3 Public Profile management. Three
 * frames mirror iOS: Updates tab (composer + recent updates), the
 * Followers tab (analytics + tier chips + follower rows), and the
 * Threads tab (one unread thread).
 */
class AudienceProfileSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2400,
                    softButtons = false,
                ),
        )

    @Test
    fun audience_profile_updates_tab() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state = sampleFrameState(activeTab = AudienceProfileTab.Updates),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_followers_tab() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state = sampleFrameState(activeTab = AudienceProfileTab.Followers),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_followers_sort_highest_tier() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        sampleFrameState(
                            activeTab = AudienceProfileTab.Followers,
                            followerSort = FollowerSort.HighestTier,
                            visibleFollowers =
                                AudienceProfileViewModel.sortFollowers(
                                    sampleLoaded().followers,
                                    FollowerSort.HighestTier,
                                ),
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_followers_search_populated() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        sampleFrameState(
                            activeTab = AudienceProfileTab.Followers,
                            searchText = "billie",
                            visibleFollowers =
                                sampleLoaded().followers.filter {
                                    it.displayName.contains("Billie", true) ||
                                        it.handle.contains("billie", true)
                                },
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_followers_search_empty() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        sampleFrameState(
                            activeTab = AudienceProfileTab.Followers,
                            searchText = "zzz",
                            visibleFollowers = emptyList(),
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_threads_tab() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        sampleFrameState(
                            activeTab = AudienceProfileTab.Threads,
                            visibleFollowers = emptyList(),
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_threads_filter_unread() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        sampleFrameState(
                            activeTab = AudienceProfileTab.Threads,
                            visibleFollowers = emptyList(),
                            activeThreadFilter = ThreadsFilter.Unread,
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_threads_filter_bronze_plus() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        sampleFrameState(
                            activeTab = AudienceProfileTab.Threads,
                            visibleFollowers = emptyList(),
                            activeThreadFilter = ThreadsFilter.BronzePlus,
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_threads_filter_flagged() {
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        sampleFrameState(
                            activeTab = AudienceProfileTab.Threads,
                            visibleFollowers = emptyList(),
                            activeThreadFilter = ThreadsFilter.Flagged,
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    @Test
    fun audience_profile_threads_filter_empty() {
        // Bronze+ filter applied to a roster of tier-1 threads — exercises
        // the filtered-empty branch (threads exist but the filter drops all).
        val loaded = sampleLoaded()
        val tierOneOnly =
            loaded.threads.map { it.copy(tierRank = 1, tierName = "Followers", flagged = false) }
        val tweaked =
            loaded.copy(
                threads = tierOneOnly,
                threadsFilterChips =
                    listOf(
                        ThreadsFilterChipContent(
                            id = ThreadsFilter.All.key,
                            filter = ThreadsFilter.All,
                            label = ThreadsFilter.All.title,
                            count = tierOneOnly.size,
                        ),
                        ThreadsFilterChipContent(
                            id = ThreadsFilter.Unread.key,
                            filter = ThreadsFilter.Unread,
                            label = ThreadsFilter.Unread.title,
                            count = tierOneOnly.count { it.unreadCount > 0 },
                        ),
                        ThreadsFilterChipContent(
                            id = ThreadsFilter.BronzePlus.key,
                            filter = ThreadsFilter.BronzePlus,
                            label = ThreadsFilter.BronzePlus.title,
                            count = 0,
                        ),
                        ThreadsFilterChipContent(
                            id = ThreadsFilter.Flagged.key,
                            filter = ThreadsFilter.Flagged,
                            label = ThreadsFilter.Flagged.title,
                            count = null,
                        ),
                    ),
            )
        paparazzi.snapshot {
            Frame {
                LoadedFrame(
                    state =
                        AudienceProfileLoadedFrameState(
                            loaded = tweaked,
                            activeTab = AudienceProfileTab.Threads,
                            composer = UpdateComposerState(),
                            selectedTier = null,
                            visibleFollowers = emptyList(),
                            activeThreadFilter = ThreadsFilter.BronzePlus,
                            visibleThreads = emptyList(),
                        ),
                    actions = sampleFrameActions(),
                )
            }
        }
    }

    private fun sampleFrameState(
        activeTab: AudienceProfileTab,
        followerSort: FollowerSort = FollowerSort.NewestActive,
        searchText: String = "",
        visibleFollowers: List<FollowerRowContent> = sampleLoaded().followers,
        activeThreadFilter: ThreadsFilter = ThreadsFilter.All,
    ): AudienceProfileLoadedFrameState {
        val loaded = sampleLoaded()
        val visibleThreads =
            loaded.threads.filter { row ->
                when (activeThreadFilter) {
                    ThreadsFilter.All -> true
                    ThreadsFilter.Unread -> row.unreadCount > 0
                    ThreadsFilter.BronzePlus -> row.tierRank >= 2
                    ThreadsFilter.Flagged -> row.flagged
                }
            }
        return AudienceProfileLoadedFrameState(
            loaded = loaded,
            activeTab = activeTab,
            composer = UpdateComposerState(),
            selectedTier = null,
            followerSearchText = searchText,
            followerSort = followerSort,
            visibleFollowers = visibleFollowers,
            activeThreadFilter = activeThreadFilter,
            visibleThreads = visibleThreads,
        )
    }

    private fun sampleFrameActions(): AudienceProfileLoadedFrameActions =
        AudienceProfileLoadedFrameActions(
            onSelectTab = {},
            onSelectTier = {},
            onFollowerSearch = {},
            onFollowerSort = {},
            onSelectThreadFilter = {},
            composer =
                AudienceProfileComposerActions(
                    onText = {},
                    onVisibility = {},
                    onTier = {},
                    onSubmit = {},
                ),
            navigation =
                AudienceProfileNavigationActions(
                    onOpenFollower = {},
                    onOpenThread = {},
                    onOpenBroadcast = { _, _ -> },
                ),
        )

    private fun sampleLoaded(): AudienceProfileLoaded =
        AudienceProfileLoaded(
            header =
                AudienceHeaderContent(
                    displayName = "Maya Builds",
                    handle = "@mayabuilds",
                    followerCount = 12,
                    newThisWeek = 3,
                    postCount = 7,
                ),
            updates =
                listOf(
                    UpdateCardContent(
                        id = "u1",
                        body = "New mural going up next week — full reveal Friday at 5pm.",
                        timeAgo = "2h ago",
                        visibility = UpdateVisibility.Followers,
                        targetTierRank = null,
                        deliveredCount = 40,
                        readCount = 31,
                    ),
                    UpdateCardContent(
                        id = "u2",
                        body = "Workshop seats just opened — Members get first pick this week.",
                        timeAgo = "1d ago",
                        visibility = UpdateVisibility.TierOrAbove,
                        targetTierRank = 2,
                        deliveredCount = 3,
                        readCount = 2,
                    ),
                ),
            analyticsCells =
                listOf(
                    AnalyticsCellContent(id = "followers", label = "Followers", value = "8"),
                    AnalyticsCellContent(id = "members", label = "Members", value = "3"),
                    AnalyticsCellContent(id = "insiders", label = "Insiders", value = "1"),
                    AnalyticsCellContent(id = "direct", label = "Direct", value = "0"),
                ),
            tierBreakdown =
                TierBreakdownContent(
                    total = 12,
                    segments =
                        listOf(
                            TierBreakdownContent.TierSegment("t1", 1, "Followers", 8),
                            TierBreakdownContent.TierSegment("t2", 2, "Members", 3),
                            TierBreakdownContent.TierSegment("t3", 3, "Insiders", 1),
                        ),
                ),
            tierChips =
                listOf(
                    TierChipContent(id = "all", rank = null, label = "All", count = 12),
                    TierChipContent(id = "tier_1", rank = 1, label = "Followers", count = 8),
                    TierChipContent(id = "tier_2", rank = 2, label = "Members", count = 3),
                    TierChipContent(id = "tier_3", rank = 3, label = "Insiders", count = 1),
                ),
            followers =
                listOf(
                    FollowerRowContent(
                        id = "m1",
                        displayName = "Alex M.",
                        handle = "@alex",
                        avatarUrl = null,
                        tierName = "Followers",
                        tierRank = 1,
                        tenureLabel = "3 mo.",
                        tenureMonths = 3,
                        joinedMonth = "2026-02",
                        verifiedLocal = true,
                    ),
                    FollowerRowContent(
                        id = "m2",
                        displayName = "Billie B.",
                        handle = "@billie",
                        avatarUrl = null,
                        tierName = "Members",
                        tierRank = 2,
                        tenureLabel = "12 mo.",
                        tenureMonths = 12,
                        joinedMonth = "2025-05",
                        verifiedLocal = false,
                    ),
                    FollowerRowContent(
                        id = "m3",
                        displayName = "Casey K.",
                        handle = "@casey",
                        avatarUrl = null,
                        tierName = "Insiders",
                        tierRank = 3,
                        tenureLabel = "6 mo.",
                        tenureMonths = 6,
                        joinedMonth = "2025-11",
                        verifiedLocal = false,
                    ),
                ),
            threads =
                listOf(
                    ThreadRowContent(
                        id = "th1",
                        displayName = "Alex M.",
                        handle = "@alex",
                        avatarUrl = null,
                        tierName = "Members",
                        tierRank = 2,
                        preview = "Loved the workshop! Any plans for July?",
                        timeAgo = "5h ago",
                        unreadCount = 2,
                        flagged = false,
                    ),
                    ThreadRowContent(
                        id = "th2",
                        displayName = "Billie B.",
                        handle = "@billie",
                        avatarUrl = null,
                        tierName = "Insiders",
                        tierRank = 3,
                        preview = "Question on step 4 — does the fold count depend on flour?",
                        timeAgo = "18m",
                        unreadCount = 1,
                        flagged = true,
                    ),
                    ThreadRowContent(
                        id = "th3",
                        displayName = "Junie L.",
                        handle = "@junie",
                        avatarUrl = null,
                        tierName = "Followers",
                        tierRank = 1,
                        preview = "Following from the market! Will swing by next time.",
                        timeAgo = "3d",
                        unreadCount = 0,
                        flagged = false,
                    ),
                ),
            threadsFilterChips =
                listOf(
                    ThreadsFilterChipContent(
                        id = ThreadsFilter.All.key,
                        filter = ThreadsFilter.All,
                        label = ThreadsFilter.All.title,
                        count = 3,
                    ),
                    ThreadsFilterChipContent(
                        id = ThreadsFilter.Unread.key,
                        filter = ThreadsFilter.Unread,
                        label = ThreadsFilter.Unread.title,
                        count = 2,
                    ),
                    ThreadsFilterChipContent(
                        id = ThreadsFilter.BronzePlus.key,
                        filter = ThreadsFilter.BronzePlus,
                        label = ThreadsFilter.BronzePlus.title,
                        count = 2,
                    ),
                    ThreadsFilterChipContent(
                        id = ThreadsFilter.Flagged.key,
                        filter = ThreadsFilter.Flagged,
                        label = ThreadsFilter.Flagged.title,
                        count = null,
                    ),
                ),
            channelId = "ch_demo",
        )

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }
}
