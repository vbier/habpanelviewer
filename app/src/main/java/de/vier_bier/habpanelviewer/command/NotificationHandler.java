package de.vier_bier.habpanelviewer.command;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.vier_bier.habpanelviewer.R;

public class NotificationHandler implements ICommandHandler {
    private static final String CHANNEL_ID = "de.vier_bier.habpanelviewer";

    private static final Pattern SHOW_PATTERN = Pattern.compile("NOTIFICATION_SHOW ([a-zA-Z]+)(.*)");
    private static final Pattern HIDE_PATTERN = Pattern.compile("NOTIFICATION_HIDE ([a-zA-Z]+)?");

    private Context mCtx;
    private NotificationManager mNotificationManager;

    public NotificationHandler(Context ctx) {
        mCtx = ctx.getApplicationContext();

        mNotificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            for (NotificationChannel c : mNotificationManager.getNotificationChannels()) {
                mNotificationManager.deleteNotificationChannel(c.getId());
            }

            for (NotificationColor c : NotificationColor.values()) {
                createChannel(c.name(), c.color());
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createChannel(String channelName, int color) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID + "." + channelName, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.enableLights(true);
        channel.setSound(null, null);
        channel.setLightColor(color);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        mNotificationManager.createNotificationChannel(channel);
    }

    @Override
    public boolean handleCommand(Command cmd) {
        final String cmdStr = cmd.getCommand();

        Matcher m = SHOW_PATTERN.matcher(cmdStr);
        Matcher m2 = HIDE_PATTERN.matcher(cmdStr);
        if (cmdStr.startsWith("NOTIFICATION_") && mNotificationManager == null) {
            cmd.failed("Android 8 or newer needed");
        } else if (m.matches()) {
            cmd.start();

            final String colorStr = m.group(1).toLowerCase();
            try {
                final String message = m.group(2);

                final NotificationColor color = NotificationColor.valueOf(colorStr);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(mCtx, CHANNEL_ID + "." + color.name());
                builder.setSmallIcon(R.drawable.logo);
                if (message != null) {
                    builder.setContentTitle(message.trim());
                }
                builder.setSound(null);
                builder.setLights(color.color(), 1000, 1000);

                mNotificationManager.notify(color.ordinal(), builder.build());
            } catch (IllegalArgumentException e) {
                cmd.failed("invalid color: " + colorStr);
            }
        } else if (m2.matches()) {
            cmd.start();
            final String colorStr = m2.group(1).toLowerCase();
            try {
                final NotificationColor color = NotificationColor.valueOf(colorStr);

                mNotificationManager.cancel(color.ordinal());
            } catch (IllegalArgumentException e) {
                cmd.failed("invalid color: " + colorStr);
            }
        } else if ("NOTIFICATION_HIDE".equals(cmdStr)) {
            cmd.start();
            for (NotificationColor c : NotificationColor.values()) {
                mNotificationManager.cancel(c.ordinal());
            }
        } else {
            return false;
        }

        cmd.finished();
        return true;
    }

    enum NotificationColor {
        white {
            @Override
            public int color() {
                return 0xffffffff;
            }
        }, red {
            @Override
            public int color() {
                return 0xffff0000;
            }
        }, green {
            @Override
            public int color() {
                return 0xff00ff00;
            }
        }, blue {
            @Override
            public int color() {
                return 0xff0000ff;
            }
        };

        public abstract int color();
    }
}
