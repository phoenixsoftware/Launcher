package com.phoenixsoftware.util;

import static com.ibm.jzos.WtoConstants.DESC_IMMEDIATE_COMMAND_RESPONSE;
import static com.ibm.jzos.WtoConstants.DESC_JOB_STATUS;
import static com.ibm.jzos.WtoConstants.ROUTCDE_MASTER_CONSOLE_INFORMATION;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.jzos.MvsConsole;
import com.ibm.jzos.MvsCommandCallback;

public class Launcher {
    
    static Process p;
    static String pidFile;
    static String launchFile;

    /**
     * Operator interaction.
     */
    static class CommandCallback implements MvsCommandCallback {
        
        private class CommandException extends Exception {
            static final long serialVersionUID = 1L;
            public CommandException(String s) {
                super(s);
            }
        }
        public void handleModify(String s) {
            String message = "";
            try {
                Pattern pattern = Pattern.compile("^display$", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(s);
                if ( ! matcher.find() )
                    throw new CommandException("Unrecognized operator command");
                String command = matcher.group(0).toUpperCase();
                if ( command.equals("DISPLAY") ) {
                    String pid = "n/a";
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(pidFile));
                        pid = Integer.valueOf(br.readLine()).toString();
                        br.close();
                    }
                    catch ( Exception e ) { e.printStackTrace(); }
                    MvsConsole.wto(launchFile + " process id = "+ pid,
                            ROUTCDE_MASTER_CONSOLE_INFORMATION,
                            DESC_JOB_STATUS );
                }
            }
            catch (CommandException e) {
                message = e.getMessage();
                e.printStackTrace();
            }
            MvsConsole.wto(message, ROUTCDE_MASTER_CONSOLE_INFORMATION, DESC_IMMEDIATE_COMMAND_RESPONSE);
        }
        public void handleStart(String params) {
            String s = "License client ready for requests.";
            MvsConsole.wto(s, ROUTCDE_MASTER_CONSOLE_INFORMATION, DESC_IMMEDIATE_COMMAND_RESPONSE);
        }
        public boolean handleStop() {
            try {
                BufferedReader br = new BufferedReader(new FileReader(pidFile));
                String s = br.readLine();
                Integer pid = Integer.valueOf(s);
                br.close();
                Process kill = Runtime.getRuntime().exec("kill -SIGINT " + pid);
                CaptureOutput stderr = new CaptureOutput( kill.getErrorStream(), System.err );
                CaptureOutput stdout = new CaptureOutput( kill.getInputStream(), System.out );
                stderr.start(); stdout.start();
                kill.waitFor(); stderr.join(); stdout.join();
                p.waitFor();
            } catch (Exception e ) { e.printStackTrace(); }
            return true;
        }
    }
    
    static class CaptureOutput extends Thread {

        private InputStream s;
        private PrintStream p;

        public CaptureOutput( InputStream s, PrintStream p ) {
            this.s = s;
            this.p = p;
        }

        @Override
        public void run() {
            try {
                InputStreamReader reader = new InputStreamReader(s);
                BufferedReader br = new BufferedReader(reader);
                String line = null;
                while ( (line = br.readLine()) != null)
                    p.println(line);
                br.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        
        CommandCallback cb = new CommandCallback();
        MvsConsole.registerMvsCommandCallback(cb);
        
        pidFile = args[0].replaceAll("^.*/([^/]+)$", ".pid.$1");
        launchFile = args[0];
        
        try {
            
            ProcessBuilder pb = new ProcessBuilder(Arrays.asList(args));
            Map<String, String> env = pb.environment();
            env.put("_BPX_JOBNAME", "SUBPROC");
            p = pb.start();
            CaptureOutput stderr = new CaptureOutput( p.getErrorStream(), System.err );
            CaptureOutput stdout = new CaptureOutput( p.getInputStream(), System.out );
            stderr.start(); stdout.start();
            stderr.join(); stdout.join();
            p.waitFor();
        }
        catch (Exception e ) {
            e.printStackTrace();
        }
        
        MvsConsole.registerMvsCommandCallback(null);
    }

}
