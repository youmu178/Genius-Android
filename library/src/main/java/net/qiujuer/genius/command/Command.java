package net.qiujuer.genius.command;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import net.qiujuer.genius.Genius;
import net.qiujuer.genius.util.Log;
import net.qiujuer.genius.util.ToolUtils;

import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by QiuJu
 * on 2014/8/13.
 */
public final class Command {
    private static final String TAG = Command.class.getName();
    //ICommandInterface
    private static ICommandInterface iService = null;
    //Intent
    private static Intent intent = null;
    //Service link class, used to instantiate the service interface
    private static ServiceConnection conn = null;
    //Lock
    private static Lock iLock = new ReentrantLock();
    private static Condition iCondition = iLock.newCondition();

    private static boolean isBindService = false;

    /**
     * init
     */
    static {
        conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                iLock.lock();
                iService = ICommandInterface.Stub.asInterface(service);
                if (iService != null) {
                    try {
                        iCondition.signalAll();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    bindService();
                }
                iLock.unlock();
                Log.i(TAG, "onServiceConnected");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                iService = null;
                Log.i(TAG, "onServiceDisconnected");
            }
        };
        bindService();
    }

    /**
     * Command the test
     *
     * @param command Command
     * @return Results
     */
    private static String command(Command command) {
        //check Service
        if (iService == null) {
            iLock.lock();
            try {
                iCondition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            iLock.unlock();
        }
        int count = 10;
        while (count > 0) {
            if (command.isCancel) {
                if (command.listener != null)
                    command.listener.onCancel();
                break;
            }
            try {
                command.result = iService.command(command.id, command.parameter);
                if (command.listener != null)
                    command.listener.onCompleted(command.result);
                break;
            } catch (Exception e) {
                count--;
                ToolUtils.sleepIgnoreInterrupt(3000);
            }
        }
        if (count <= 0) {
            if (command.listener != null)
                command.listener.onError();
            bindService();
        }
        command.listener = null;
        return command.result;
    }


    /**
     * start bind Service
     */
    private synchronized static void bindService() {
        Context context = Genius.getApplication();
        if (context == null) {
            throw new NullPointerException("ApplicationContext is not null.Please setApplicationContext()");
        } else {
            if (isBindService)
                dispose();
            if (intent == null)
                intent = new Intent(context, CommandService.class);
            context.startService(intent);
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE);
            isBindService = true;
        }
    }

    /**
     * *********************************************************************************************
     * Static public
     * *********************************************************************************************
     * /**
     * Command the test
     *
     * @param command Command
     * @return Results
     */
    public static String command(final Command command, CommandListener listener) {
        if (listener == null) {
            return command(command);
        } else {
            command.listener = listener;
            Thread thread = new Thread() {
                @Override
                public void run() {
                    command(command);
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
        return null;
    }

    /**
     * cancel Test
     */
    public static void cancel(Command command) {
        command.isCancel = true;
        if (iService != null)
            try {
                iService.cancel(command.id);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
    }

    /**
     * dispose unbindService stopService
     */
    public static void dispose() {
        if (iService != null) {
            try {
                iService.destroy();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            iService = null;
        }
        Context context = Genius.getApplication();
        if (context != null) {
            try {
                context.unbindService(conn);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            if (intent != null) {
                context.stopService(intent);
                intent = null;
            }
        }
        isBindService = false;
    }

    /**
     * *********************************************************************************************
     * Class
     * *********************************************************************************************
     */
    private String id;
    private String parameter = null;
    private boolean isCancel = false;
    private CommandListener listener = null;
    private String result = null;

    /**
     * Get a Command
     *
     * @param params params eg: "/system/bin/ping", "-c", "4", "-s", "100","www.qiujuer.net"
     */
    public Command(String... params) {
        //check params
        if (params == null)
            throw new NullPointerException("params is not null.");
        //run
        StringBuilder sb = new StringBuilder();
        for (String str : params) {
            sb.append(str);
            sb.append(" ");
        }
        this.parameter = sb.toString();
        this.id = UUID.randomUUID().toString();
    }

    /**
     * CommandListener
     */
    public interface CommandListener {
        public void onCompleted(String str);

        public void onCancel();

        public void onError();
    }
}