import sys
import os
from kazoo.client import KazooClient

#if (len(sys.argv)<2):
#	sys.exit(0)
#os.chdir(os.path.dirname(os.path.abspath(__file__)))
seed=109.231.85.85
mngr_master=109.231.85.43:2181
if (mngr_master.find(".")<0)
	print "getRing$ ERROR: Not correct IP address format"
	sys.exit()
if (mngr_master.find(":")<0)
	print "getRing$ ERROR: Cannot locate port number"
	sys.exit()
print "Seed="+seed+"\nConf_Managet="+mngr_master
zk = KazooClient(hosts=mngr_master)
zk.start()
p = os.popen('sh bin/nodetool -h '+seed+' ring')
output=p.read()
p.close()
output=output.split('Token')[1].translate(None," ").translate(None,"")
output=output.replace("\n", "|")
i=0
s=output.split("|")
st=""
while i<len(s):
	if "%" in s[i]:
		st=st+s[i].split("rack")[0]+"<>"+s[i].split("%")[1]+"|"
	i=i+1
print "\n\n"+st+"\n\n"
if zk.exists("/cassRing"):
	data, stat = zk.get("/cassRing")
	print("Version: %s, data: %s" % (stat.version, data.decode("utf-8")))	
	zk.set("/cassRing", st)
else:
	zk.create("/cassRing", st)
print zk.get("/cassRing")[0]


