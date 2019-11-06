package com.phoenixsoftware.util;

import static com.ibm.jzos.Enqueue.ISGENQ_CONTROL_EXCLUSIVE;
import static com.ibm.jzos.Enqueue.ISGENQ_CONTROL_SHARED;
import static com.ibm.jzos.Enqueue.ISGENQ_SCOPE_STEP;
import static com.ibm.jzos.Enqueue.ISGENQ_SCOPE_SYSSPLEX;
import static com.ibm.jzos.Enqueue.ISGENQ_SCOPE_SYSTEM;
import static com.ibm.jzos.Enqueue.ISGENQ_SCOPE_SYSTEMS;
import static com.ibm.jzos.WtoConstants.DESC_IMMEDIATE_COMMAND_RESPONSE;
import static com.ibm.jzos.WtoConstants.DESC_JOB_STATUS;
import static com.ibm.jzos.WtoConstants.ROUTCDE_MASTER_CONSOLE_INFORMATION;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.jzos.MvsConsole;
import com.ibm.jzos.Enqueue;
import com.ibm.jzos.MvsCommandCallback;
import com.ibm.jzos.RcException;

public class Launcher {

    static AtomicBoolean stopped = new AtomicBoolean(false);
    static Process p;
    static String launchFile;
    static int returnCode = 0;
    static int pidUnknown;

    static class CommandCallback implements MvsCommandCallback {
        
        private class CommandException extends Exception {
            static final long serialVersionUID = 1L;
            public CommandException(String s) {
                super(s);
            }
        }
        public void handleModify(String s) {
            try {
                Pattern pattern = Pattern.compile("^display$", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(s);
                if ( ! matcher.find() )
                    throw new CommandException("Unrecognized operator command");
                String command = matcher.group(0).toUpperCase();
                if ( command.equals("DISPLAY") ) {
                    MvsConsole.wto(launchFile + " process id = " + getProcessId(),
                        ROUTCDE_MASTER_CONSOLE_INFORMATION,
                        DESC_JOB_STATUS);
                }
            }
            catch (CommandException e) {
                e.printStackTrace();
                MvsConsole.wto(e.getMessage(),
                        ROUTCDE_MASTER_CONSOLE_INFORMATION,
                        DESC_JOB_STATUS);
            }
        }
        public void handleStart(String params) {
        }
        public boolean handleStop() {
            stopped.set(true);
            signalTerm();
            return false;
        }
    }
    
    static class CaptureOutput extends Thread {

        private InputStream inStream;
        private PrintStream printStream;

        public CaptureOutput( InputStream inStream, PrintStream printStream ) {
            this.inStream = inStream;
            this.printStream = printStream;
        }

        @Override
        public void run() {
            try {
                InputStreamReader reader = new InputStreamReader(inStream);
                BufferedReader br = new BufferedReader(reader);
                String line = null;
                while ( (line = br.readLine()) != null)
                    printStream.println(line);
                br.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class ArgumentException extends Exception {
        private static final long serialVersionUID = 1L;
        public ArgumentException(String s) {
            super(s);
        }
    }

    static class Arguments {

        ArrayList<String> files = new ArrayList<String>();
        boolean keepAlive = false;
        boolean autoRestart = false;
        int control = ISGENQ_CONTROL_SHARED;
        int scope = ISGENQ_SCOPE_STEP;
        int hour = 0;
        int minute = 0;

        public Arguments(String [] args) throws ArgumentException {
            for ( int i = 0 ; i < args.length ; i++ ) {
                if ( args[i].startsWith("-") ) {
                    if ( args[i].equals("-e") )
                        control = ISGENQ_CONTROL_EXCLUSIVE;
                    else if ( args[i].equals("-p") )
                        scope = ISGENQ_SCOPE_SYSSPLEX;
                    else if ( args[i].equals("-m") )
                        scope = ISGENQ_SCOPE_SYSTEM;
                    else if ( args[i].equals("-s") )
                        scope = ISGENQ_SCOPE_SYSTEMS;
                    else if ( args[i].equals("-k") )
                        keepAlive = true;
                    else if ( args[i].equals("-r") ) {
                        if ( ++i < args.length && ! args[i].startsWith("-") ) {
                            Pattern pattern = Pattern.compile("^(\\d\\d):(\\d\\d)$");
                            Matcher matcher = pattern.matcher(args[i]);
                            if ( matcher.find() ) {
                                hour = Integer.parseInt(matcher.group(1));
                                minute = Integer.parseInt(matcher.group(2));
                                autoRestart = true;
                            }
                            else
                                throw new ArgumentException("Invalid value: "+args[i-1]);
                        }
                        else
                            throw new ArgumentException("Missing value: "+args[i-1]);
                    }
                    else
                        throw new ArgumentException("Unrecognized option: "+args[i]);
                }
                else {
                    files.add(args[i]);
                }
            }
        }
    }

    static synchronized long getProcessId() {
        long pid = pidUnknown;
        try  {
            pid = p.pid();
        }
        catch (Throwable requiresAtLeastJava9) {
            try {
                if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
                    Field f = p.getClass().getDeclaredField("pid");
                    f.setAccessible(true);
                    pid = f.getLong(p);
                    f.setAccessible(false);
                }
            }
            catch (Exception securityManagerDeniedAccess) {
                pid = pidUnknown;
            }
        }
        return pid;
    }

    static synchronized void signalTerm() {
            long pid = getProcessId();
            if ( ( pid != pidUnknown ) )
                try { Runtime.getRuntime().exec("/bin/kill -TERM " + pid); }
                catch (IOException e) { p.destroy(); }
            else {
                p.destroy();
            }
            System.err.println("Sent TERM signal");
    }

    public static void main(String[] arguments) {

        class RestartProcessTask extends TimerTask {
            public void run() {
                signalTerm();
            }
        }

        if ( ! MvsConsole.isListening() )
            MvsConsole.startMvsCommandListener();
        CommandCallback cb = new CommandCallback();
        MvsConsole.registerMvsCommandCallback(cb);
        Timer timer = new Timer("RestartTimer");

        try {
            Arguments args = new Arguments(arguments); 
            launchFile = args.files.get(0);

            Enqueue enq = new Enqueue("LAUNCHER", launchFile);
            enq.setContentionActFail();
            enq.setControl(args.control);
            enq.setScope(args.scope);

            try {
                enq.obtain();
                try {
                    do { 
                        if ( args.autoRestart ) {
                            Calendar calendar = Calendar.getInstance();
                            int target = ((args.hour * 3600) + (args.minute * 60));
                            int current = ((calendar.get(Calendar.HOUR_OF_DAY) * 3600)
                                + (calendar.get(Calendar.MINUTE) * 60)
                                + calendar.get(Calendar.SECOND));
                            int delay = (target + ( (current + 120) > target ? 86400 : 0 )) - current;
                            timer.cancel();
                            timer = new Timer("RestartTimer");
                            timer.schedule(new RestartProcessTask(), delay * 1000);
                        }
                        ProcessBuilder pb = new ProcessBuilder(args.files);
                        System.err.println("Launching process");
                        MvsConsole.wto("Launching process "+launchFile,
                            ROUTCDE_MASTER_CONSOLE_INFORMATION,
                            DESC_IMMEDIATE_COMMAND_RESPONSE);
                        p = pb.start();
                        System.err.println("Waiting for work");
                        MvsConsole.wto("Waiting for work",
                            ROUTCDE_MASTER_CONSOLE_INFORMATION,
                            DESC_IMMEDIATE_COMMAND_RESPONSE);
                        CaptureOutput stderr = new CaptureOutput( p.getErrorStream(), System.err );
                        CaptureOutput stdout = new CaptureOutput( p.getInputStream(), System.out );
                        stderr.start(); stdout.start();
                        stderr.join(); stdout.join();
                        p.waitFor();
                        System.err.println("Process ended: "+p.exitValue());
                    } while ( args.keepAlive && ! stopped.get() );
                }
                catch (Exception e ) {
                    e.printStackTrace();
                }

                enq.release();
                returnCode = 0;
            }
            catch (RcException e) {
                String message = "QNAME="+enq.getQName()+", RNAME="+enq.getRName()+", "+e.getLocalizedMessage();
                MvsConsole.wto(message,
                    ROUTCDE_MASTER_CONSOLE_INFORMATION,
                    DESC_IMMEDIATE_COMMAND_RESPONSE);
                returnCode = 8;
            }
        }
        catch (ArgumentException e) {
            MvsConsole.wto(e.getMessage(),
                ROUTCDE_MASTER_CONSOLE_INFORMATION,
                DESC_IMMEDIATE_COMMAND_RESPONSE);
            returnCode = 8;
        }

        timer.cancel();
        MvsConsole.registerMvsCommandCallback(null);
        System.exit(returnCode);
    }

}
