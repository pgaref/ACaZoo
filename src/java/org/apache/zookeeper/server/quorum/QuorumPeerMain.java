/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.server.quorum;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

import javax.management.JMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.OutputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooDefs.OpCode;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.jmx.ManagedUtil;
import org.apache.zookeeper.proto.CreateRequest;
import org.apache.zookeeper.server.ByteBufferInputStream;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.DatadirCleanupManager;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.RequestProcessor.RequestProcessorException;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig.ConfigException;
import org.apache.zookeeper.server.util.ZxidUtils;
import org.apache.zookeeper.txn.CreateTxn;
import org.apache.zookeeper.txn.TxnHeader;

/**
 * 
 * <h2>Configuration file</h2>
 * 
 * When the main() method of this class is used to start the program, the first
 * argument is used as a path to the config file, which will be used to obtain
 * configuration information. This file is a Properties file, so keys and values
 * are separated by equals (=) and the key/value pairs are separated by new
 * lines. The following is a general summary of keys used in the configuration
 * file. For full details on this see the documentation in docs/index.html
 * <ol>
 * <li>dataDir - The directory where the ZooKeeper data is stored.</li>
 * <li>dataLogDir - The directory where the ZooKeeper transaction log is stored.
 * </li>
 * <li>clientPort - The port used to communicate with clients.</li>
 * <li>tickTime - The duration of a tick in milliseconds. This is the basic unit
 * of time in ZooKeeper.</li>
 * <li>initLimit - The maximum number of ticks that a follower will wait to
 * initially synchronize with a leader.</li>
 * <li>syncLimit - The maximum number of ticks that a follower will wait for a
 * message (including heartbeats) from the leader.</li>
 * <li>server.<i>id</i> - This is the host:port[:port] that the server with the
 * given id will use for the quorum protocol.</li>
 * </ol>
 * In addition to the config file. There is a file in the data directory called
 * "myid" that contains the server id as an ASCII decimal value.
 * 
 */
public class QuorumPeerMain {
	private static final Logger LOG = LoggerFactory
			.getLogger(QuorumPeerMain.class);

	private static final String USAGE = "Usage: QuorumPeerMain configfile";
	private static final String[] ZooArguments = { "./conf/zoo.cfg" };
	public static QuorumPeer quorumPeer;
	public static ServerCnxnFactory cnxnFactory;

	/**
	 * To start the replicated server specify the configuration file name on the
	 * command line.
	 * 
	 * @param args
	 *            path to the configfile
	 */
	public static void main(String[] args) {
		QuorumPeerMain main = new QuorumPeerMain();
		System.setProperty("jute.maxbuffer", "104857600");
		try {
			LOG.info("pgaref Mod starts here!");
			LOG.debug("pgaref debug Mod starts here!");
			main.initializeAndRun(args);
		} catch (IllegalArgumentException e) {
			LOG.error("Invalid arguments, exiting abnormally", e);
			LOG.info(USAGE);
			System.err.println(USAGE);
			System.exit(2);
		} catch (ConfigException e) {
			LOG.error("Invalid config, exiting abnormally", e);
			System.err.println("Invalid config, exiting abnormally");
			System.exit(2);
		} catch (Exception e) {
			LOG.error("Unexpected exception, exiting abnormally", e);
			System.exit(1);
		}
		LOG.info("Exiting normally");
		System.exit(0);
	}

	protected void initializeAndRun(String[] args) throws ConfigException,
			IOException {

		/*
		 * pgaref
		 */
		args = ZooArguments;
		QuorumPeerConfig config = new QuorumPeerConfig();
		Thread mymod = new Thread(new Myclass(1));

		if (args.length == 1) {
			config.parse(args[0]);
		}

		// Start and schedule the the purge task
		DatadirCleanupManager purgeMgr = new DatadirCleanupManager(
				config.getDataDir(), config.getDataLogDir(),
				config.getSnapRetainCount(), config.getPurgeInterval());
		purgeMgr.start();
		mymod.start();
		if (args.length == 1 && config.servers.size() > 0) {
			runFromConfig(config);
		} else {
			LOG.warn("Either no config or no quorum defined in config, running "
					+ " in standalone mode");
			// there is only server in the quorum -- run as standalone

			ZooKeeperServerMain.main(args);

		}
		mymod.start();
	}

	public void runFromConfig(QuorumPeerConfig config) throws IOException {
		try {
			ManagedUtil.registerLog4jMBeans();
		} catch (JMException e) {
			LOG.warn("Unable to register log4j JMX control", e);
		}

		LOG.info("Starting quorum peer");
		try {
			cnxnFactory = ServerCnxnFactory.createFactory();
			cnxnFactory.configure(config.getClientPortAddress(),
					config.getMaxClientCnxns());

			quorumPeer = new QuorumPeer();
			quorumPeer.setClientPortAddress(config.getClientPortAddress());
			quorumPeer.setTxnFactory(new FileTxnSnapLog(new File(config
					.getDataLogDir()), new File(config.getDataDir())));
			quorumPeer.setQuorumPeers(config.getServers());
			quorumPeer.setElectionType(config.getElectionAlg());
			quorumPeer.setMyid(config.getServerId());
			quorumPeer.setTickTime(config.getTickTime());
			quorumPeer.setMinSessionTimeout(config.getMinSessionTimeout());
			quorumPeer.setMaxSessionTimeout(config.getMaxSessionTimeout());
			quorumPeer.setInitLimit(config.getInitLimit());
			quorumPeer.setSyncLimit(config.getSyncLimit());
			quorumPeer.setQuorumVerifier(config.getQuorumVerifier());
			quorumPeer.setCnxnFactory(cnxnFactory);
			quorumPeer
					.setZKDatabase(new ZKDatabase(quorumPeer.getTxnFactory()));
			quorumPeer.setLearnerType(config.getPeerType());

			quorumPeer.start();
			quorumPeer.join();
		} catch (InterruptedException e) {
			// warn, but generally this is ok
			LOG.warn("Quorum Peer interrupted", e);
		}
	}

	/*
	 * pgaref Zookeeper Server Accessor
	 */
	public static ZooKeeperServer getZkServer(ServerCnxnFactory fac) {
		return fac.zkServer;
	}

	public static ServerCnxnFactory getConFactory() {
		return cnxnFactory;
	}

	public void CreateInternal(String blockname, byte[] data) {
		// String blockname = "/foo";
		// String data = "pgaref";
		LOG.info("pgaref: Create Internal Called from Sync BENCHMARK");
		int i = 0;
		// pgaref -> 23 is the byte len of ZooDefs.Ids.OPEN_ACL_UNSAFE
		int DataHeaderLength = 16 + blockname.length() + data.length + 23;
		// ByteBuffer Requestdata = ByteBuffer.allocate(DataHeaderLength);
		ByteBuffer Requestdata = ByteBuffer.wrap(new byte[DataHeaderLength]);
		try {

			Requestdata.clear();
			// path name len
			Requestdata.putInt((blockname.length()));
			// path name
			Requestdata.put(blockname.getBytes());
			// data len
			Requestdata.putInt(data.length);
			// data
			Requestdata.put(data);
			// acl null
			Requestdata.putInt(ZooDefs.Ids.OPEN_ACL_UNSAFE.size());
			for (int index = 0; index < ZooDefs.Ids.OPEN_ACL_UNSAFE.size(); index++) {
				org.apache.zookeeper.data.ACL e1 = ZooDefs.Ids.OPEN_ACL_UNSAFE
						.get(index);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OutputArchive boa = BinaryOutputArchive.getArchive(baos);
				boa.writeRecord(e1, null);
				Requestdata.put(baos.toByteArray());
			}

			/*
			 * ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 * DataOutputStream dos = new DataOutputStream(baos);
			 * BinaryOutputArchive archive = new BinaryOutputArchive(dos); try {
			 * ZooDefs.Ids.OPEN_ACL_UNSAFE.get(0).serialize(archive,"");
			 * 
			 * } catch (IOException e) { LOG.info("serialization Exception: "+
			 * e); } Requestdata.put(archive.toString().getBytes());
			 */
			// the flags
			Requestdata.putInt(CreateMode.PERSISTENT.toFlag());
			Requestdata.flip();
		} catch (IOException ex) {
			LOG.info("pgaref - Exception Serializing ACL List");
		} catch (BufferOverflowException ex) {
			LOG.info("BufferOverflowException: " + ex);
		}

		/* DATA End here */

		long zxid = ZxidUtils.makeZxid(1, i);
		TxnHeader hdr = new TxnHeader(1, 10 + i, zxid, 30 + i,
				ZooDefs.OpCode.create);
		Record txn = new CreateTxn(blockname, data,
				ZooDefs.Ids.OPEN_ACL_UNSAFE, false, 1);
		Request req = new Request(null, 2285l, 1, OpCode.create, Requestdata,
				null);
		req.hdr = hdr;
		req.txn = txn;

		/*
		 * CreateRequest createRequest = new CreateRequest(); try {
		 * ByteBufferInputStream.byteBuffer2Record(req.request, createRequest);
		 * } catch (IOException e1) {
		 * LOG.info("pgaref -Serialization request Known error"); }
		 * 
		 * LOG.info("pgaref - Lets see : " + createRequest.toString() +
		 * " Path: " + createRequest.getPath() + " Data: " +
		 * createRequest.getData().toString() + " ACL: "+
		 * createRequest.getAcl().toString() + " Flags: "+
		 * createRequest.getFlags());
		 */
		// FOR QUORUM
		// QuorumPeerMain.quorumPeer.getActiveServer().submitRequest(req);

		// FOR STANDALONE SERVER
		try {
			ZooKeeperServer.finalProcessor.processRequest(req);
		} catch (RequestProcessorException e) {
			LOG.debug("pgaref request error" + e);
		}
		LOG.info("is going to process!!!");
	}

	public static class Myclass implements Runnable {

		int ReqNum;

		public Myclass(int req) {
			LOG.info("pgaref Thread Constr called!");
			ReqNum = req;
		}

		@Override
		public void run() {
			LOG.info("pgaref Thread RUN called!");
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
				LOG.info("Thead SLEEP Exception");
			}

			/*
			 * Some Notes: Create Sequential 2013-10-18 23:18:21,811 [myid:] -
			 * DEBUG [SyncThread:0:FinalRequestProcessor@88] - Processing
			 * request:: sessionid:0x141cd36ac530000 type:create cxid:0x11
			 * zxid:0x2 txntype:1 reqpath:n/a 2013-10-18 23:18:21,855 [myid:] -
			 * DEBUG [SyncThread:0:FinalRequestProcessor@160] -
			 * sessionid:0x141cd36ac530000 type:create cxid:0x11 zxid:0x2
			 * txntype:1 reqpath:n/a
			 */
			int i = 0;
			while (true) {

				if (QuorumPeerMain.quorumPeer.getServerState()
						.equalsIgnoreCase("LEADING")) {

					LOG.info("pgaref - LEADING!!!!");
					while ((ReqNum) > 0) {

						// ServerCnxnFactory cnxn =
						// QuorumPeerMain.getConFactory();
						// ZooKeeperServer serv =
						// QuorumPeerMain.getZkServer(cnxn);
						/*
						 * ByteArrayOutputStream baos = new
						 * ByteArrayOutputStream(); String tosend =
						 * "pgaref"+ReqNum;
						 * 
						 * try { DataOutputStream out = new
						 * DataOutputStream(baos); out.writeBytes(tosend);
						 * out.close(); } catch (IOException e) {
						 * LOG.info("pgaref - Master: Serializer exception!");
						 * e.printStackTrace(); }
						 */

						/*
						 * Request foo = new Request(null, 1l, 1, OpCode.create,
						 * ByteBuffer.wrap(baos.toByteArray()), null);
						 * 
						 * Request bar = new Request(null, 141cd36ac530000, 2,
						 * OpCode.create, ByteBuffer.wrap(baos.toByteArray()),
						 * null); try {
						 * ZooKeeperServer.getRequestProcessor().processRequest
						 * (foo); } catch (RequestProcessorException e) {
						 * LOG.info
						 * ("pgaref - Master: (send) Process Request exception!"
						 * ); }
						 */
						/* They are not replicated! */
						// DataTree tmp = ZooKeeperServer.zkDb.getDataTree();

						/*
						 * ALlocate Memory for Request DATA MUST ADD ZOOKEEPER
						 * FLAGS
						 */
						String blockname = "/foo";
						String data = "pgaref";

						// pgaref -> 23 is the byte len of
						// ZooDefs.Ids.OPEN_ACL_UNSAFE + int headers are 16 byte
						// but will cause exception lower than 18
						int DataHeaderLength = 18 + blockname.length()
								+ data.length() + 23;
						// ByteBuffer Requestdata =
						// ByteBuffer.allocate(DataHeaderLength);
						ByteBuffer Requestdata = ByteBuffer
								.wrap(new byte[DataHeaderLength]);
						try {

							Requestdata.clear();
							// path name len
							Requestdata.putInt((blockname.length()));
							// path name
							Requestdata.put(blockname.getBytes());
							// data len
							Requestdata.putInt(data.length());
							// data
							Requestdata.put(data.getBytes());
							// acl null
							Requestdata.putInt(ZooDefs.Ids.OPEN_ACL_UNSAFE
									.size());
							for (int index = 0; index < ZooDefs.Ids.OPEN_ACL_UNSAFE
									.size(); index++) {
								org.apache.zookeeper.data.ACL e1 = ZooDefs.Ids.OPEN_ACL_UNSAFE
										.get(index);
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								OutputArchive boa = BinaryOutputArchive
										.getArchive(baos);
								boa.writeRecord(e1, null);
								Requestdata.put(baos.toByteArray());
							}

							/*
							 * ByteArrayOutputStream baos = new
							 * ByteArrayOutputStream(); DataOutputStream dos =
							 * new DataOutputStream(baos); BinaryOutputArchive
							 * archive = new BinaryOutputArchive(dos); try {
							 * ZooDefs
							 * .Ids.OPEN_ACL_UNSAFE.get(0).serialize(archive
							 * ,"");
							 * 
							 * } catch (IOException e) {
							 * LOG.info("serialization Exception: "+ e); }
							 * Requestdata.put(archive.toString().getBytes());
							 */
							// the flags
							Requestdata.putInt(CreateMode.PERSISTENT_SEQUENTIAL
									.toFlag());
							Requestdata.flip();
						} catch (IOException ex) {
							LOG.info("pgaref - Exception Serializing ACL List");
						} catch (BufferOverflowException ex) {
							LOG.info("BufferOverflowException: " + ex);
						}

						/* DATA End here */

						long zxid = ZxidUtils.makeZxid(1, i);
						TxnHeader hdr = new TxnHeader(1, 10 + i, zxid, 30 + i,
								ZooDefs.OpCode.create);
						Record txn = new CreateTxn("/foo" + i,
								"pgaref".getBytes(),
								ZooDefs.Ids.OPEN_ACL_UNSAFE, false, 1);
						Request req = new Request(null, 2285l, 1,
								OpCode.create, Requestdata, null);
						req.hdr = hdr;
						req.txn = txn;

						/*
						 * CreateRequest createRequest = new CreateRequest();
						 * try {
						 * ByteBufferInputStream.byteBuffer2Record(req.request,
						 * createRequest); } catch (IOException e1) {
						 * LOG.info("pgaref -Serialization request Known error"
						 * ); }
						 * 
						 * LOG.info("pgaref - Lets see : " +
						 * createRequest.toString() + " Path: " +
						 * createRequest.getPath() + " Data: " +
						 * createRequest.getData().toString() + " ACL: "+
						 * createRequest.getAcl().toString() + " Flags: "+
						 * createRequest.getFlags());
						 */
						// FOR QUORUM
						QuorumPeerMain.quorumPeer.getActiveServer()
								.submitRequest(req);

						/*
						 * FOR STANDALONE SERVER try {
						 * ZooKeeperServer.finalProcessor.processRequest(req); }
						 * catch (RequestProcessorException e) {
						 * LOG.debug("pgaref request error"+ e); }
						 * LOG.info("is going to process!!!");
						 */
						// QuorumPeerMain.getZkServer(cnxnFactory).zkDb.addCommittedProposal(new
						// Request(null, 1l, 1, OpCode.create,
						// ByteBuffer.wrap(baos.toByteArray()), null));
						/*
						 * tmp.createNode("/pgaref", "pgaref data".getBytes(),
						 * null, 0, tmp.getNode("/").stat.getCversion()+1, 1,
						 * 1); } catch (NoNodeException e) {
						 * LOG.info("pgaref Error! - "+e); } catch
						 * (NodeExistsException e) {
						 * LOG.info("pgaref Error - "+e); }
						 */
						i++;
						try {
							LOG.info("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS");
							Thread.sleep(15000);
						} catch (InterruptedException e) {
							LOG.info("pgaref sleeep Thread error!");
						}

					}
				}

				else {
					LOG.info("pgaref - FOLLOWING!!!! - "
							+ QuorumPeerMain.quorumPeer.getServerState());
				}
				try {
					LOG.info("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS");
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					LOG.info("pgaref sleeep Thread error!");
				}
			}
		}
	}
}
