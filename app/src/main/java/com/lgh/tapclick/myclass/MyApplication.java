package com.lgh.tapclick.myclass;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.lgh.tapclick.mybean.MyAppConfig;
import com.lgh.tapclick.myfunction.MyUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import me.weishu.reflection.Reflection;

public class MyApplication extends Application {
    private static final AtomicReference<Thread> DATABASE_THREAD = new AtomicReference<>();
    private static final ExecutorService DATABASE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TapClick-Database");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        DATABASE_THREAD.set(thread);
        return thread;
    });
    private static final AtomicInteger IO_THREAD_NUMBER = new AtomicInteger();
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(2, runnable ->
            new Thread(runnable, "TapClick-IO-" + IO_THREAD_NUMBER.incrementAndGet()));

    private static Handler mainHandler;
    public static DataDao dataDao;

    public interface DatabaseCallback<T> {
        void onResult(T result);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Reflection.unseal(base);
        mainHandler = new Handler(Looper.getMainLooper());

        if (dataDao == null) {
            Migration migration_1_2 = new Migration(1, 2) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE 'Widget' ADD COLUMN 'lastClickTime' INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE 'Widget' ADD COLUMN 'clickCount' INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE 'Coordinate' ADD COLUMN 'lastClickTime' INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE 'Coordinate' ADD COLUMN 'clickCount' INTEGER NOT NULL DEFAULT 0");
                }
            };
            Migration migration_2_3 = new Migration(2, 3) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE 'Widget' ADD COLUMN 'clickNumber' INTEGER NOT NULL DEFAULT 1");
                    database.execSQL("ALTER TABLE 'Widget' ADD COLUMN 'clickInterval' INTEGER NOT NULL DEFAULT 500");
                }
            };
            Migration migration_3_4 = new Migration(3, 4) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE 'Widget' ADD COLUMN 'action' INTEGER NOT NULL DEFAULT 0");
                }
            };
            Migration migration_4_5 = new Migration(4, 5) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE 'Widget' RENAME COLUMN 'lastClickTime' TO 'lastTriggerTime'");
                    database.execSQL("ALTER TABLE 'Widget' RENAME COLUMN 'clickCount' TO 'triggerCount'");
                }
            };
            Migration migration_5_6 = new Migration(5, 6) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE 'Coordinate' RENAME COLUMN 'lastClickTime' TO 'lastTriggerTime'");
                    database.execSQL("ALTER TABLE 'Coordinate' RENAME COLUMN 'clickCount' TO 'triggerCount'");
                }
            };
            Migration migration_6_7 = new Migration(6, 7) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE 'Widget' ADD COLUMN 'toast' TEXT");
                    database.execSQL("ALTER TABLE 'Coordinate' ADD COLUMN 'toast' TEXT");
                    database.execSQL("ALTER TABLE 'Widget' ADD COLUMN 'condition' INTEGER NOT NULL DEFAULT 0");
                }
            };
            DataDao roomDataDao = Room.databaseBuilder(base, MyDatabase.class, "applicationData.db")
                    .addMigrations(migration_1_2, migration_2_3, migration_3_4, migration_4_5, migration_5_6, migration_6_7)
                    .build()
                    .dataDao();
            dataDao = new SerializedDataDao(roomDataDao);
        }

        Future<?> configFuture = DATABASE_EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                MyAppConfig myAppConfig = dataDao.getMyAppConfig();
                if (myAppConfig == null) {
                    dataDao.insertMyAppConfig(new MyAppConfig());
                }
            }
        });
        try {
            configFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("初始化应用配置时线程被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("初始化应用数据库失败", e.getCause());
        }

        MyUtils.init(base);
    }

    public static void executeDatabase(Runnable runnable) {
        if (Thread.currentThread() == DATABASE_THREAD.get()) {
            runnable.run();
        } else {
            DATABASE_EXECUTOR.execute(runnable);
        }
    }

    public static <T> void queryDatabase(Callable<T> callable, DatabaseCallback<T> callback) {
        DATABASE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    T result = callable.call();
                    postToMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(result);
                        }
                    });
                } catch (Exception e) {
                    throw new IllegalStateException("数据库任务执行失败", e);
                }
            }
        });
    }

    public static void postToMain(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public static void executeIo(Runnable runnable) {
        IO_EXECUTOR.execute(runnable);
    }

    static <T> T callDatabase(Callable<T> callable) {
        if (Thread.currentThread() == DATABASE_THREAD.get()) {
            return callUnchecked(callable);
        }
        Future<T> future = DATABASE_EXECUTOR.submit(callable);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待数据库任务时线程被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("数据库任务执行失败", cause);
        }
    }

    private static <T> T callUnchecked(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("数据库任务执行失败", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MyUncaughtExceptionHandler.getInstance(this).install();
    }
}
