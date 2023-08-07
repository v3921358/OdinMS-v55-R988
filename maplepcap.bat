@echo off
set CLASSPATH=.;dist\odinms.jar;dist\exttools.jar;mina-core.jar;slf4j-api.jar;slf4j-jdk14.jar;mysql-connector-java-bin.jar;jpcap.jar
java -Dnet.sf.odinms.recvops=recvops.properties -Dnet.sf.odinms.sendops=sendops.properties net.sf.odinms.exttools.maplepcap.MaplePcap blahh3.pcap
pause