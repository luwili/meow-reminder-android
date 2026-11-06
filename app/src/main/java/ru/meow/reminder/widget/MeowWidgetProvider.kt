package ru.meow.reminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.meow.reminder.MainActivity
import ru.meow.reminder.MeowApp
import ru.meow.reminder.R
import ru.meow.reminder.data.DoseOccurrence
import ru.meow.reminder.data.MedicationTemplate
import ru.meow.reminder.data.PhraseLibrary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MeowWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TAKE_DOSE) {
            val id = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
            val app = context.applicationContext as? MeowApp ?: return
            app.scope.launch {
                try {
                    app.scheduler.handleNotificationAction(id, "TAKEN")
                } catch (_: Exception) {
                    // Fallback to direct repo mark if scheduler is busy
                    app.repository.mark(id, "TAKEN")
                }
                updateAllWidgets(context)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val app = context.applicationContext as? MeowApp ?: return
        app.scope.launch {
            val data = queryWidgetData(app, context)
            val views = buildResponsiveViews(context, appWidgetManager, appWidgetId, data)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        const val ACTION_TAKE_DOSE = "ru.meow.reminder.widget.ACTION_TAKE_DOSE"
        const val EXTRA_OCCURRENCE_ID = "ru.meow.reminder.widget.EXTRA_OCCURRENCE_ID"

        fun updateAllWidgets(context: Context) {
            val app = context.applicationContext as? MeowApp ?: return
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, MeowWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            app.scope.launch {
                val data = queryWidgetData(app, context)
                for (id in ids) {
                    val views = buildResponsiveViews(context, appWidgetManager, id, data)
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
        }

        private suspend fun queryWidgetData(app: MeowApp, context: Context): WidgetData = withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val nowMillis = System.currentTimeMillis()
            val today = LocalDate.now(zone)
            val allOccurrences = app.repository.dao.allOccurrences()
            val medications = app.repository.dao.allMedications().associateBy { it.id }
            val phrases = app.repository.dao.allPhrases()
            val settings = app.repository.settings.get()

            val dayDoses = allOccurrences.filter {
                Instant.ofEpochMilli(it.plannedAt).atZone(zone).toLocalDate() == today &&
                    (!it.cancelled || it.status != "PENDING")
            }.sortedBy { it.plannedAt }

            val todayPending = dayDoses.filter { !it.cancelled && it.status == "PENDING" }
            val todayOverdue = todayPending.filter { it.plannedAt <= nowMillis }.sortedBy { it.plannedAt }
            val todayUpcoming = todayPending.filter { it.plannedAt > nowMillis }.sortedBy { it.plannedAt }

            val upcomingAfterToday = allOccurrences.filter {
                !it.cancelled && it.status == "PENDING" &&
                    Instant.ofEpochMilli(it.plannedAt).atZone(zone).toLocalDate() > today
            }.sortedBy { it.plannedAt }

            val totalToday = dayDoses.size
            val takenToday = dayDoses.count { it.status == "TAKEN" }

            val heroDose: DoseOccurrence?
            val heroBadgeText: String
            val isOverdue: Boolean

            when {
                todayOverdue.isNotEmpty() -> {
                    heroDose = todayOverdue.first()
                    heroBadgeText = context.getString(R.string.widget_badge_due)
                    isOverdue = true
                }
                todayUpcoming.isNotEmpty() -> {
                    heroDose = todayUpcoming.first()
                    heroBadgeText = context.getString(R.string.widget_badge_next)
                    isOverdue = false
                }
                upcomingAfterToday.isNotEmpty() -> {
                    heroDose = upcomingAfterToday.first()
                    heroBadgeText = context.getString(R.string.widget_badge_future)
                    isOverdue = false
                }
                else -> {
                    heroDose = null
                    heroBadgeText = if (totalToday > 0) "Все принято ✨" else "Нет приёмов"
                    isOverdue = false
                }
            }

            val heroMed = heroDose?.let { medications[it.medicationId] }
            val heroTime = heroDose?.let {
                try {
                    LocalDateTime.parse(it.localDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))
                } catch (_: Exception) {
                    Instant.ofEpochMilli(it.plannedAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                }
            } ?: ""

            val heroDesc = if (heroMed != null) {
                val dosagePart = if (heroMed.dosage.isNotBlank()) "${heroMed.dosage} ${heroMed.unit}".trim() else ""
                val purposePart = heroMed.purpose.takeIf { it.isNotBlank() } ?: ""
                listOf(dosagePart, purposePart).filter { it.isNotBlank() }.joinToString(" · ")
            } else ""

            val upcomingList = dayDoses.filter { it.id != heroDose?.id }.take(3).map {
                it to medications[it.medicationId]
            }

            val dateFormatted = try {
                val ruLocale = Locale.forLanguageTag("ru")
                today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", ruLocale)).replaceFirstChar { it.uppercase(ruLocale) }
            } catch (_: Exception) {
                today.toString()
            }

            val pickedPhrase = PhraseLibrary.pick(app.repository.settings, phrases).ifEmpty {
                "Мяу! Не забудь позаботиться о себе 🐾"
            }

            WidgetData(
                todayFormatted = dateFormatted,
                totalToday = totalToday,
                takenToday = takenToday,
                heroDose = heroDose,
                heroMed = heroMed,
                heroTimeFormatted = heroTime,
                heroBadgeText = heroBadgeText,
                heroBadgeIsOverdue = isOverdue,
                heroDesc = heroDesc,
                upcomingDoses = upcomingList,
                phrase = pickedPhrase
            )
        }

        private fun buildResponsiveViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: WidgetData
        ): RemoteViews {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val viewMap = mapOf(
                    SizeF(110f, 48f) to buildCompactViews(context, data),
                    SizeF(180f, 100f) to buildMediumViews(context, data),
                    SizeF(220f, 180f) to buildLargeViews(context, data)
                )
                RemoteViews(viewMap)
            } else {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
                val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
                when {
                    minWidth >= 180 && minHeight >= 170 -> buildLargeViews(context, data)
                    minWidth >= 180 -> buildMediumViews(context, data)
                    else -> buildCompactViews(context, data)
                }
            }
        }

        private fun getOpenAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getQuickAddPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_QUICK_ADD)
                data = Uri.parse("meow://widget/quick_add")
            }
            return PendingIntent.getActivity(
                context,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getTakeDosePendingIntent(context: Context, doseId: String): PendingIntent {
            val intent = Intent(context, MeowWidgetProvider::class.java).apply {
                action = ACTION_TAKE_DOSE
                putExtra(EXTRA_OCCURRENCE_ID, doseId)
                data = Uri.parse("meow://widget/take/${Uri.encode(doseId)}")
            }
            return PendingIntent.getBroadcast(
                context,
                doseId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildCompactViews(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout_compact)
            val openApp = getOpenAppPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_root_compact, openApp)

            val hero = data.heroDose
            if (hero != null) {
                val medName = data.heroMed?.name ?: hero.medicationName
                views.setTextViewText(R.id.widget_compact_title, "${data.heroTimeFormatted} · $medName")
                views.setTextViewText(
                    R.id.widget_compact_subtitle,
                    if (data.heroDesc.isNotBlank()) data.heroDesc
                    else if (data.heroBadgeIsOverdue) context.getString(R.string.widget_badge_due)
                    else "Ожидает приёма"
                )
                views.setViewVisibility(R.id.widget_compact_action_btn, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.widget_compact_action_btn, getTakeDosePendingIntent(context, hero.id))
            } else {
                views.setTextViewText(
                    R.id.widget_compact_title,
                    if (data.totalToday > 0) context.getString(R.string.widget_all_done_title)
                    else context.getString(R.string.widget_no_doses)
                )
                views.setTextViewText(
                    R.id.widget_compact_subtitle,
                    if (data.totalToday > 0) context.getString(R.string.widget_all_taken)
                    else "Нажмите, чтобы открыть"
                )
                views.setViewVisibility(R.id.widget_compact_action_btn, View.GONE)
            }
            return views
        }

        private fun buildMediumViews(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout_medium)
            val openApp = getOpenAppPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_root_medium, openApp)

            val addPending = getQuickAddPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_medium_btn_add, addPending)

            // Header badge
            if (data.heroBadgeIsOverdue) {
                views.setTextViewText(R.id.widget_medium_badge, context.getString(R.string.widget_badge_due))
                views.setInt(R.id.widget_medium_badge, "setBackgroundResource", R.drawable.widget_badge_overdue_background)
                views.setTextColor(R.id.widget_medium_badge, context.getColor(R.color.widget_badge_overdue_text))
            } else {
                val badgeText = if (data.totalToday > 0) "${data.takenToday}/${data.totalToday} принято" else "Мяу 🐾"
                views.setTextViewText(R.id.widget_medium_badge, badgeText)
                views.setInt(R.id.widget_medium_badge, "setBackgroundResource", R.drawable.widget_badge_background)
                views.setTextColor(R.id.widget_medium_badge, context.getColor(R.color.widget_badge_ok_text))
            }

            val hero = data.heroDose
            if (hero != null) {
                views.setViewVisibility(R.id.widget_medium_hero_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_medium_empty_container, View.GONE)

                views.setTextViewText(R.id.widget_medium_hero_time, data.heroTimeFormatted)
                views.setTextViewText(R.id.widget_medium_hero_name, data.heroMed?.name ?: hero.medicationName)
                views.setTextViewText(
                    R.id.widget_medium_hero_desc,
                    if (data.heroDesc.isNotBlank()) data.heroDesc else "Запланированный приём"
                )
                views.setOnClickPendingIntent(
                    R.id.widget_medium_hero_action_btn,
                    getTakeDosePendingIntent(context, hero.id)
                )
            } else {
                views.setViewVisibility(R.id.widget_medium_hero_container, View.GONE)
                views.setViewVisibility(R.id.widget_medium_empty_container, View.VISIBLE)

                views.setTextViewText(
                    R.id.widget_medium_empty_title,
                    if (data.totalToday > 0) context.getString(R.string.widget_all_done_title)
                    else context.getString(R.string.widget_no_doses)
                )
                views.setTextViewText(
                    R.id.widget_medium_empty_subtitle,
                    if (data.totalToday > 0) context.getString(R.string.widget_all_taken)
                    else "Нажмите +, чтобы добавить напоминание"
                )
            }
            return views
        }

        private fun buildLargeViews(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout_large)
            val openApp = getOpenAppPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_root_large, openApp)

            val addPending = getQuickAddPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_large_btn_add, addPending)

            views.setTextViewText(R.id.widget_large_date, data.todayFormatted)
            val progressText = if (data.totalToday > 0) "${data.takenToday} из ${data.totalToday} принято" else "Мяу 🐾"
            views.setTextViewText(R.id.widget_large_progress, progressText)

            val hero = data.heroDose
            if (hero != null) {
                views.setViewVisibility(R.id.widget_large_hero_box, View.VISIBLE)
                views.setViewVisibility(R.id.widget_large_all_taken_box, View.GONE)

                views.setTextViewText(R.id.widget_large_hero_badge, data.heroBadgeText)
                if (data.heroBadgeIsOverdue) {
                    views.setInt(R.id.widget_large_hero_badge, "setBackgroundResource", R.drawable.widget_badge_overdue_background)
                    views.setTextColor(R.id.widget_large_hero_badge, context.getColor(R.color.widget_badge_overdue_text))
                } else {
                    views.setInt(R.id.widget_large_hero_badge, "setBackgroundResource", R.drawable.widget_badge_background)
                    views.setTextColor(R.id.widget_large_hero_badge, context.getColor(R.color.widget_badge_ok_text))
                }

                views.setTextViewText(R.id.widget_large_hero_time, data.heroTimeFormatted)
                views.setTextViewText(R.id.widget_large_hero_name, data.heroMed?.name ?: hero.medicationName)
                views.setTextViewText(
                    R.id.widget_large_hero_dosage,
                    if (data.heroDesc.isNotBlank()) data.heroDesc else "Запланированный приём"
                )
                views.setOnClickPendingIntent(
                    R.id.widget_large_hero_action_btn,
                    getTakeDosePendingIntent(context, hero.id)
                )
            } else {
                views.setViewVisibility(R.id.widget_large_hero_box, View.GONE)
                views.setViewVisibility(R.id.widget_large_all_taken_box, View.VISIBLE)
            }

            // Upcoming list
            val upcoming = data.upcomingDoses
            if (upcoming.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_large_upcoming_empty, View.GONE)
                val (item1, med1) = upcoming[0]
                views.setViewVisibility(R.id.widget_large_item_1, View.VISIBLE)
                val time1 = try {
                    LocalDateTime.parse(item1.localDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))
                } catch (_: Exception) { "" }
                views.setTextViewText(R.id.widget_large_item_1_time, time1)
                views.setTextViewText(R.id.widget_large_item_1_name, med1?.name ?: item1.medicationName)
                views.setTextViewText(
                    R.id.widget_large_item_1_status,
                    if (item1.status == "TAKEN") "Принято ✓" else if (item1.status == "SKIPPED") "Пропущено" else "Ожидает"
                )

                if (upcoming.size > 1) {
                    val (item2, med2) = upcoming[1]
                    views.setViewVisibility(R.id.widget_large_item_2, View.VISIBLE)
                    val time2 = try {
                        LocalDateTime.parse(item2.localDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (_: Exception) { "" }
                    views.setTextViewText(R.id.widget_large_item_2_time, time2)
                    views.setTextViewText(R.id.widget_large_item_2_name, med2?.name ?: item2.medicationName)
                    views.setTextViewText(
                        R.id.widget_large_item_2_status,
                        if (item2.status == "TAKEN") "Принято ✓" else if (item2.status == "SKIPPED") "Пропущено" else "Ожидает"
                    )
                } else {
                    views.setViewVisibility(R.id.widget_large_item_2, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_large_item_1, View.GONE)
                views.setViewVisibility(R.id.widget_large_item_2, View.GONE)
                views.setViewVisibility(R.id.widget_large_upcoming_empty, View.VISIBLE)
            }

            views.setTextViewText(R.id.widget_large_phrase, data.phrase)
            return views
        }
    }
}

private data class WidgetData(
    val todayFormatted: String,
    val totalToday: Int,
    val takenToday: Int,
    val heroDose: DoseOccurrence?,
    val heroMed: MedicationTemplate?,
    val heroTimeFormatted: String,
    val heroBadgeText: String,
    val heroBadgeIsOverdue: Boolean,
    val heroDesc: String,
    val upcomingDoses: List<Pair<DoseOccurrence, MedicationTemplate?>>,
    val phrase: String
)
