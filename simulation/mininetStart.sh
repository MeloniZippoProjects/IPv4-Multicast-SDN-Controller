#!/bin/sh
if [ -z $1 ]; then echo "Usage: mininetStart.sh {controller ip address}{ip base}"; exit; else echo "Controller ip address set to '$1'; ip base set to '$2'"; fi
sudo mn --topo single,3 --mac --switch ovsk --controller remote,ip=$1,port=6653,protocols=OpenFlow13 --nat
