# Launcher
Java program to launch a Node.js or other script on z/OS.

This program is intended to be run as a started task (STC) — see sample JCL below — so that a Node.js program can be controlled with operator START (S) and STOP (P) commands. The name of the target script and its arguments are specified in the PARM field.

__The target script is expected to implement a TERM signal handler__ to effect an orderly shut down. See the wait4stop sample code.

By default the launcher will create a STEP level SHARED ENQ with a QNAME of LAUNCHER and an RNAME equal to the target script path. This can be changed via optional flags to, for example, prevent more than one instance of target script.

Also by default when the target script terminates the Launcher STC will end. This can be changed via optional flags to, for example, restart the target script automatically at a given time.

Any or all of the optional flags shown here may be specified in the parm field:

Flag|Description
----|-----------
-e|Changes the ENQ resource control from SHARED to EXCLUSIVE.
-k|Restart the target script when it ends (except as a result of an operator STOP command).
-m|Changes the ENQ scope from STEP to SYSTEM.
-p|Changes the ENQ scope from STEP to SYSPLEX.
-r hh:mm|Restart the target script at the indicated time. N.B., without the -k flag the script will terminate at the indicated time but will not restart.
-s|Changes the ENQ scope from STEP to SYSTEMS.

## Sample JCL Procedure for STC:

```
//LAUNCHER PROC
//*
// SET JAVACLS='com.phoenixsoftware.util.Launcher'
// SET FLAGS='-r 01:00 -k -e -p' 
// SET SCRIPT='/path/to/script'
// SET ARGS='arg1 arg2 arg3'
// SET LIBRARY='JVB800.SIEALNKE'         < STEPLIB FOR JVMLDM module
// SET VERSION='86'                      < JVMLDM version: 86
// SET LOGLVL='+D'                       < Debug LVL: +I(info) +T(trc)
// SET REGSIZE='0M'                      < EXECUTION REGION SIZE
// SET LEPARM=''
//*
//JAVAJVM  EXEC PGM=JVMLDM&VERSION,REGION=&REGSIZE,TIME=NOLIMIT,
//   PARM='&LEPARM/&LOGLVL &JAVACLS &FLAGS &SCRIPT &ARGS'
//STEPLIB  DD DSN=&LIBRARY,DISP=SHR
//STDENV   DD *,DLM='%~'
. /etc/profile
export JAVA_HOME=/usr/lpp/java/current_64/
export PATH=/bin:"${JAVA_HOME}"/bin:
LIBPATH=/lib:/usr/lib:"${JAVA_HOME}"/bin
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin/classic
export LIBPATH="$LIBPATH":
CP="${JAVA_HOME}/lib/tools.jar"
CP="$CP":"/path/to/launcher.jar"
export CLASSPATH="$CP":
export IBM_JAVA_OPTIONS="-Xms64m -Xmx128m"
%~
//SYSPRINT DD SYSOUT=*          < System stdout
//STDOUT   DD SYSOUT=*          < Java System.out
//STDERR   DD SYSOUT=*          < Java System.err
//SYSOUT   DD SYSOUT=*          < System stderr
//CEEDUMP  DD SYSOUT=*
//ABNLIGNR DD DUMMY
//SYSMDUMP DD SYSOUT=*
//*
```
Be sure to set /path/to/script and /path/to/launcher.jar in the procedure, as well as any arguments the target script requires.
