#! /bin/bash +x

SCRIPT_DIR=$(dirname $BASH_SOURCE)
SCRIPTNAME=`basename $0`

APP_DIR=/apps/chunking
LOGS_DIR=${APP_DIR}/logs

MAIN_CLASS="com.kerumai.chunking.Launcher"

echo "SCRIPT_DIR=$SCRIPT_DIR"
echo "SCRIPTNAME=$SCRIPTNAME"

#############################################################
let GB=`free -m | grep '^Mem:' | awk '{print $2}'`\*80/102400
HEAP="$GB"g

JAVA_OPTS="-Xmx$HEAP -Xms$HEAP \
-Dzuul.app.dir=${APP_DIR} \
"

################ Classpath #############################
# Add conf dir and all jars from lib directory to the classpath.
LIB="${APP_DIR}/lib"
CP=""
for f in ${LIB}/*.jar; do
  CP=${CP}:${f}
done

# Enable netty leak detection.
JAVA_OPTS="${JAVA_OPTS} -Dio.netty.leakDetectionLevel=advanced"

java ${JAVA_OPTS} -cp ${CP} ${MAIN_CLASS} 

