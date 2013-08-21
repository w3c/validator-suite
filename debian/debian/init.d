#!/bin/sh
### BEGIN INIT INFO
# Provides:          vs
# Required-Start:    $remote_fs $syslog
# Required-Stop:     $remote_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: start/stop vs application
### END INIT INFO

# Author: Alexandre Bertails <bertails@w3.org>

PATH="${PATH:+$PATH:}/usr/sbin:/sbin"
DESC="start/stop vs server"
NAME="vs"
DAEMON="/usr/bin/java"
DAEMON_ARGS='-Dhttp.port=8080 -Dconfig.file="conf/application.conf" -cp "lib/*" play.core.server.NettyServer "/usr/local/vs"'
PIDFILE="/usr/local/vs/RUNNING_PID"
SCRIPTNAME=/etc/init.d/$NAME
USER="play"
APPDIR="/usr/local/vs"
export JAVA_OPTS="-server"

# Load the VERBOSE setting and other rcS variables
. /lib/init/vars.sh

# Define LSB log_* functions.
# Depend on lsb-base (>= 3.2-14) to ensure that this file is present
# and status_of_proc is working.
. /lib/lsb/init-functions

#
# Function that starts the daemon/service
#
do_start()
{
    start-stop-daemon --start --quiet --background --chdir $APPDIR --verbose --chuid $USER --pidfile $PIDFILE --exec $DAEMON --test > /dev/null \
        || return 1
    echo $DAEMON_ARGS | xargs start-stop-daemon --start --quiet --background --chdir $APPDIR --verbose --chuid $USER --pidfile $PIDFILE --exec $DAEMON -- \
        || return 2
    LOGFILE="$APPDIR/logs/application.log"
    for i in 3 2 1; do
        if [ ! -e "$LOGFILE" ]; then
            sleep 1
        else
            break
        fi
    done
    wgrep 60 "$LOGFILE" 'play - Application started (Prod)'
    RETVAL=$?
    if [ $RETVAL -ne 0 ] || [ ! -e "$LOGFILE" ]; then
        rm -f "$PIDFILE"
        return 2
    fi
    return $RETVAL
}

#
# Function that stops the daemon/service
#
do_stop()
{
    start-stop-daemon --stop --quiet --chuid $USER --oknodo --pidfile $PIDFILE
    RETVAL="$?"
    [ "$RETVAL" = 2 ] && return 2
    rm -f $PIDFILE
    return "$RETVAL"
}

case "$1" in
  start)
	[ "$VERBOSE" != no ] && log_daemon_msg "Starting $DESC" "$NAME"
	do_start
	case "$?" in
		0|1) [ "$VERBOSE" != no ] && log_end_msg 0 ;;
		2) [ "$VERBOSE" != no ] && log_end_msg 1 ;;
	esac
	;;
  stop)
	[ "$VERBOSE" != no ] && log_daemon_msg "Stopping $DESC" "$NAME"
	do_stop
	case "$?" in
		0|1) [ "$VERBOSE" != no ] && log_end_msg 0 ;;
		2) [ "$VERBOSE" != no ] && log_end_msg 1 ;;
	esac
	;;
  status)
	status_of_proc "$DAEMON" "$NAME" && exit 0 || exit $?
	;;
  restart|force-reload)
	log_daemon_msg "Restarting $DESC" "$NAME"
	do_stop
	case "$?" in
	  0|1)
		do_start
		case "$?" in
			0) log_end_msg 0 ;;
			1) log_end_msg 1 ;; # Old process is still running
			*) log_end_msg 1 ;; # Failed to start
		esac
		;;
	  *)
		# Failed to stop
		log_end_msg 1
		;;
	esac
	;;
  *)
	echo "Usage: $SCRIPTNAME {start|stop|status|restart|force-reload}" >&2
	exit 3
	;;
esac

:
