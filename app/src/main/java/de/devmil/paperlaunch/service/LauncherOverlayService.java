package de.devmil.paperlaunch.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.util.List;

import de.devmil.paperlaunch.R;
import de.devmil.paperlaunch.SettingsActivity;
import de.devmil.paperlaunch.model.IEntry;
import de.devmil.paperlaunch.model.LaunchConfig;
import de.devmil.paperlaunch.storage.EntriesDataSource;
import de.devmil.paperlaunch.utils.ViewUtils;
import de.devmil.paperlaunch.view.LauncherView;

public class LauncherOverlayService extends Service {
    private static final String ACTION_LAUNCH = "ACTION_LAUNCH";
    private static final int NOTIFICATION_ID = 2000;

    private boolean mNotificationShown = false;
    private boolean mAlreadyRegistered = false;
    private EntriesDataSource mDataSource;
    private LauncherView mLauncherView;
    private boolean mIsLauncherActive = false;

    public LauncherOverlayService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null && ACTION_LAUNCH.equals(intent.getAction())) {
            ensureOverlayActive();
            ensureNotification();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public static void launch(Context context) {
        Intent launchServiceIntent = new Intent(context, LauncherOverlayService.class);
        launchServiceIntent.setAction(ACTION_LAUNCH);
        context.startService(launchServiceIntent);
    }

    private void ensureOverlayActive() {
        boolean alreadyRegistered = mAlreadyRegistered;

        if(alreadyRegistered) {
            return;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int)ViewUtils.getPxFromDip(this, 5),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.RIGHT;

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        final LinearLayout touchReceiver = new LinearLayout(this);
        touchReceiver.setBackgroundColor(Color.TRANSPARENT);

        touchReceiver.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleTouch(touchReceiver, event);
            }
        });

        wm.addView(touchReceiver, params);

        mAlreadyRegistered = true;
    }

    private LauncherView createLauncherView(MotionEvent event) {
        LauncherView result = new LauncherView(this);
        final LaunchConfig cfg = new LaunchConfig();
        getDataSource().executeWithOpenDataSource(new EntriesDataSource.IAction() {
            @Override
            public void execute() {
                List<IEntry> entries = getDataSource().loadRootContent();
                cfg.setEntries(entries);
            }
        });
        result.doInitialize(cfg);
        result.doAutoStart(event);

        return result;
    }

    private EntriesDataSource getDataSource() {
        if(mDataSource == null) {
            mDataSource = new EntriesDataSource(this);
        }
        return mDataSource;
    }

    private boolean handleTouch(final LinearLayout touchReceiver, final MotionEvent event) {
        if(!mIsLauncherActive) {
            mLauncherView = createLauncherView(event);
            Rect hitRect = new Rect();
            touchReceiver.getHitRect(hitRect);
            if(!hitRect.contains((int)event.getX(), (int)event.getY()))
                return false;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);

            final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

            wm.addView(mLauncherView, params);
            mIsLauncherActive = true;
            mLauncherView.setListener(new LauncherView.ILauncherViewListener() {
                @Override
                public void onFinished() {
                    if (mLauncherView != null) {
                        wm.removeView(mLauncherView);
                        mLauncherView = null;
                    }
                    mIsLauncherActive = false;
                }
            });

            mLauncherView.requestLayout();

            mLauncherView.start();
        } else {
            transferMotionEvent(touchReceiver, mLauncherView, event);
        }

        return true;
    }

    private void transferMotionEvent(View from, LauncherView to, MotionEvent event) {
        float fromX = event.getX();
        float fromY = event.getY();

        int[] toLocation = new int[2];
        to.getLocationOnScreen(toLocation);
        int[] fromLocation = new int[2];
        from.getLocationOnScreen(fromLocation);

        float newX = fromX + (fromLocation[0] - toLocation[0]);
        float newY = fromY + (fromLocation[1] - toLocation[1]);

        to.handleTouchEvent(event.getAction(), newX, newY);
    }

    private void ensureNotification() {
        if(mNotificationShown) {
            return;
        }
        PendingIntent settingsPendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, SettingsActivity.class),
                0
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle("PaperLaunch")
                .setContentText("PaperLaunch running")
                .setOngoing(true)
                .setLocalOnly(true)
                .setSmallIcon(R.drawable.empty)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(settingsPendingIntent)
        ;

        Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);

        mNotificationShown = true;

    }
}
