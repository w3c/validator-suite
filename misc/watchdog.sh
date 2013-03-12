#!/bin/sh
#
# Based on apache-watchdog by gerald
#
# jetty-watchdog: watchdog to make sure jetty is responding on port 8080
#
# sample usage: */5 * * * * /usr/local/sbin/jetty-watchdog
#
# to disable this monitor for a short period of time (e.g. a few hours
# while you work on something), run:
#
#    $ touch /etc/no_monitor
#
# to re-enable it later:
#
#    $ rm /etc/no_monitor
#
# to disable this monitor for some period of time longer than 1 day,
# comment out the appropriate entry in root's crontab.
#
# Gerald Oskoboiny, 17 Oct 2009
#
# $Id: apache-watchdog,v 1.5 2009/11/24 00:14:22 gerald Exp $
#

# erase any old no_monitor files that may have been left around by accident
if [ x != x"`find /etc -maxdepth 1 -type f -name no_monitor -mtime +1`" ]; then
    rm -f /etc/no_monitor
    echo "&sysreq found a stale no_monitor file on $HOSTNAME: `ls -l /etc/no_monitor`; removed by apache-watchdog" | nc -q0 irccat.w3.org 1101
fi

# exit here if no monitor currently wanted
if [ -f /etc/no_monitor ]; then
    exit
fi

# try three times to get a '200 OK' response; exit if we get at least one
for attempt in 1 2 3 ; do
    HEAD -t 30 http://localhost:8080/unicorn 2> /dev/null | grep -q '200 OK' && exit 0
    sleep 10
done

# no response, so restart apache after killing any lingering processes
( date
  /etc/init.d/tomcat5.5 stop
  sleep 15
  # regular stop didn't work, let's kill the process
  killall -q jsvc
  sleep 15
  killall -q -9 jsvc
  sleep 5
  /etc/init.d/tomcat5.5 start
) 2>&1 >> /tmp/tomcat-watchdog.log

# bark about what we did, like a good little watchdog
echo "&unicorn tomcat was unresponsive on $(hostname); restarted by tomcat-watchdog" | nc -q0 irccat.w3.org 1101
