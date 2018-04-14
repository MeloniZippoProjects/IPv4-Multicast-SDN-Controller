#!/usr/bin/python                                                                            
                                                                                             
from mininet.topo import Topo
from mininet.net import Mininet
from mininet.util import dumpNodeConnections
from mininet.log import setLogLevel
from mininet.node import RemoteController
from mininet.node import OVSSwitch
from mininet.node import Host
from mininet.topo import SingleSwitchTopo
from functools import partial
from mininet.cli import CLI
import sys

def createNetwork():
    "Create and test a simple network"
    topo = SingleSwitchTopo(k=3)
    switch = partial( OVSSwitch, protocols='OpenFlow13' )
    controller = partial( RemoteController, ip=sys.argv[1], port=6653)
    net = Mininet(topo=topo, switch=OVSSwitch, controller=controller,autoSetMacs=True)
    net.start()
    print("Dumping host connections")
    dumpNodeConnections(net.hosts)

    print("Dumping host ips")
    for host in net.hosts:
        print(" - " + host.name + ": " + host.IP())

    print("Adding default gw to hosts")
    for host in net.hosts:
        host.cmd("route add default gw " + sys.argv[2])
        print(" - " + host.name + ": DONE")
    print("Testing network connectivity")
    net.pingAll()
    CLI(net)
    net.stop()

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: ./mnStart.py {controller address} {default gateway address}")
        sys.exit()
    
    # Tell mininet to print useful information
    setLogLevel('info')
    createNetwork()
