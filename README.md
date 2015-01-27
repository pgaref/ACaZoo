#ACaZoo is a Distributed Key-Value Store based on Replicated LSM-Trees 

It combines the names of the two systems it derives from: Apache
Cassandra and Apache ZooKeeper

You can read more about the system [here](http://www.doc.ic.ac.uk/~pg1712/papers/srds14.pdf)

##Some useful Notes 

---------Cassandra Environment Setup-------------------

    sudo mkdir -p /var/log/cassandra 
    sudo chown -R `whoami` /var/log/cassandra 
    sudo mkdir -p /var/lib/cassandra 
    sudo chown -R `whoami` /var/lib/cassandra 



------------IPV4 FIX - if needed---------------------
    /etc/gai.conf which is the configuration for getaddrinfo(). 
    There you have to uncomment line ~54 which reads: “precedence ::ffff:0:0/96  100″


---------Java Enviroment Installation--------
    First download an oracle JDK >=1.7, lets say 17u17
    tar -xvf jdk-7u17-linux-x64.tar.gz
    sudo mkdir /usr/lib/jvm 
    sudo mv jdk1.7.0_17/ /usr/lib/jvm/
    sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/jdk1.7.0_17/bin/java 1065 
    sudo update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/jdk1.7.0_17/bin/javac 1065 
    sudo update-alternatives --config java

    JAVA_HOME=/usr/lib/jvm/java-7-oracle

---------Java Dependencies--------

    sudo apt-get install ant1.7
    sudo apt-get install ant-optional
    sudo apt-get install maven2

=====================Startup Scripts=================
    eclipse project ==> ant build 

    sudo mkdir /var/lib/cassandra
    sudo chown pgaref:pgaref /var/lib/cassandra/
    sudo mkdir /var/log/cassandra/
    sudo chown pgaref:pgaref /var/log/cassandra/

    ./bin/cassandra -f

=====================Murmur Token generator=================

    python -c 'print [((2**64 / X) * i) - 2**63 for i in range(X)]' 

=====================Enable remote JMX=================
    
    #pgaref was here
    JVM_OPTS="$JVM_OPTS -Djava.rmi.server.hostname=109.231.124.27"

++++++++++++++++++++++++++++Cassandra CQL Client: 

    bin/cqlsh

~~~~~~~~~~~~~~~Cassandra CLI commands for YCSB ~~~~~~~~~~~~~~~``
    #Disabling autocompaction in a keyspace
    ./bin/nodetool disableautocompaction usertable data


    #Create ycsb keyspace for testing!
    CREATE KEYSPACE usertable
        with placement_strategy = 'org.apache.cassandra.locator.SimpleStrategy'
        and strategy_options = {replication_factor:3};

    USE usertable;


    CREATE COLUMN FAMILY data with column_type = 'Standard'    and comparator = 'UTF8Type'    and default_validation_class =
    'UTF8Type'    and key_validation_class = 'UTF8Type'    and read_repair_chance = 0.1    and dclocal_read_repair_chance = 0.0
    and caching = 'ALL';
    
    quit;
    
    CREATE COLUMN FAMILY data with column_type = 'Standard'    and comparator = 'UTF8Type'    and default_validation_class =
    'UTF8Type'    and key_validation_class = 'UTF8Type'    and caching = 'ALL';


    #Tested with Cassandra 2.0, CQL client for YCSB framework
    #In CQLSH, create keyspace and table.  Something like:

    create keyspace ycsb WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor': 5 };
 
    use ycsb;

     create table usertable (
         y_id varchar primary key,
         field0 varchar,
         field1 varchar,
         field2 varchar,
         field3 varchar,
         field4 varchar,
         field5 varchar,
         field6 varchar,
         field7 varchar,
         field8 varchar,
         field9 varchar);
    
 ----------------------------------   
        INSERT INTO users (user_id,  fname, lname)
        VALUES (1745, 'john', 'smith');

        INSERT INTO users (user_id,  fname, lname)
        VALUES (1744, 'john', 'doe');

        INSERT INTO users (user_id,  fname, lname)
        VALUES (1746, 'john', 'smith');
  
  
  
 ~How to Load:
  ./bin/ycsb load cassandra-10 -P workloads/workloada -p hosts=109.231.124.27 -threads 50 -s
 ~How to RUN:
  ./bin/ycsb run cassandra-10 -P workloads/workloada -p hosts=acazoo1,acazoo2,acazoo3 -threads 256 -s

  
  ./bin/ycsb run cassandra-10 -P workloads/workloadb -p hosts=acazoo1,acazoo2,acazoo3 -threads 256 -s > WriteSerial1G-cassandra-256
  
++++++++++++++++++++++++++++++Zookeeper Client: 

    bin/zkCli.sh -server 127.0.0.1:2181		      

    ls /

    create /zk_test my_data


===================== CASSMAIL ===================================

    create keyspace mail with placement_strategy = 'SimpleStrategy'
        and strategy_options = {replication_factor:3};
    
    create column family mailboxes with comparator = 'TimeUUIDType';
    create column family users with comparator = 'BytesType';

    
    INSERT INTO Mailboxes (user, email, user_id)
	values ('jbellis', 'jbellis@datastax.com', 1)
	IF NOT EXISTS
	
	===>POSTAL
		postal -M50 -m500 -t32 "[acazoo1]8025" users.txt
		postal -M200 -m2000 -t32 "[acazoo1]8025" users.txt
