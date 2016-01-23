#!/bin/sh

# Centos / Red Hat / Amazon Linux
# check that owner group exists
if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker spinnaker -m --home-dir /home/spinnaker
fi

chkconfig --add clouddriver
chkconfig --level 2345 clouddriver on

install --mode=755 --owner=spinnaker --group=spinnaker --directory  /var/log/spinnaker/clouddriver 
