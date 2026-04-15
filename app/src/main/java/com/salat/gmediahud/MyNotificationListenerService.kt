package com.salat.gmediahud

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MyNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    }
}
