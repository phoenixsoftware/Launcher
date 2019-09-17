# Launcher
Java program to launch a Node.js or other script on z/OS.

This program is intended to be run from the JSOZ Launcher (see sample JCL below) so that the JSOZ classes are available. The name of the target script is specified in the PARM field.

__The target script is expected to write its process id to a file__ called .pid-scriptname in the startup directory, where scriptname is file name of the script. This allows the Launcher to send a HUP signal to the target script process when an operator STOP (P) command is received. Of course, __the target script is also expected to implement a HUP signal handler__ to effect an orderly shut down.

## Sample JCL Procedure for STC:

```
//EJESLICN PROC
//*
// SET JAVACLS='com.phoenixsoftware.util.Launcher'
// SET ARGS='/path/to/script arg1 arg2 arg3'
// SET LIBRARY='JVB800.SIEALNKE'         < STEPLIB FOR JVMLDM module
// SET VERSION='86'                      < JVMLDM version: 86
// SET LOGLVL='+D'                       < Debug LVL: +I(info) +T(trc)
// SET REGSIZE='0M'                      < EXECUTION REGION SIZE
// SET LEPARM=''
//*               
//JAVAJVM  EXEC PGM=JVMLDM&VERSION,REGION=&REGSIZE,TIME=NOLIMIT,
//   PARM='&LEPARM/&LOGLVL &JAVACLS &ARGS'                   
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
