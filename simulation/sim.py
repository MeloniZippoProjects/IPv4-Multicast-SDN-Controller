#!/usr/bin/python                                                                            
                                                                                             
from mininet.topo import Topo
from mininet.net import Mininet
from mininet.util import dumpNodeConnections
from mininet.log import setLogLevel
from mininet.node import RemoteController
from functools import partial
from mininet.cli import CLI
import sys
class SingleSwitchTopo(Topo):
    "Single switch connected to n hosts."
    def build(self, n=2):
        switch = self.addSwitch('s1')
        # Python's range(N) generates 0..N-1
        for h in range(n):
            host = self.addHost('h%s' % (h + 1))
            self.addLink(host, switch)

def createNetwork():
    "Create and test a simple network"
    topo = SingleSwitchTopo(n=3)
    net = Mininet(topo=topo, controller=partial( RemoteController, ip=sys.argv[1], port=6653))
    net.start()
    print("Dumping host connections")
    dumpNodeConnections(net.hosts)

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
