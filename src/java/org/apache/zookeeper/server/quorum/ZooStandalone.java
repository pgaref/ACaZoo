package org.apache.zookeeper.server.quorum;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.OutputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooDefs.OpCode;
import org.apache.zookeeper.server.DatadirCleanupManager;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.RequestProcessor.RequestProcessorException;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig.ConfigException;
import org.apache.zookeeper.server.util.ZxidUtils;
import org.apache.zookeeper.txn.CreateTxn;
import org.apache.zookeeper.txn.TxnHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooStandalone implements ZooEmbedded ,Runnable {
	private static final Logger LOG = LoggerFactory
			.getLogger(ZooStandalone.class);

	private static final String[] ZooArguments = { "./conf/zooConf/zooStand.cfg" };


	@Override
	public void init() {
		
		LOG.info("ZooStandalone init called!");
		
		@SuppressWarnings("unused")
		ZooStandalone zoo = new ZooStandalone();
		System.setProperty("jute.maxbuffer", "104857600");

		String[] args = ZooArguments;
		QuorumPeerConfig config = new QuorumPeerConfig();

		if (args.length == 1) {
			try {
				config.parse(args[0]);
			} catch (ConfigException e) {
				LOG.error("Error parsing configuration file!");
			}
		}

		// Start and schedule the the purge task
		DatadirCleanupManager purgeMgr = new DatadirCleanupManager(
				config.getDataDir(), config.getDataLogDir(),
				config.getSnapRetainCount(), config.getPurgeInterval());
		purgeMgr.start();
		LOG.info("No quorum defined in config, running "
				+ " in standalone mode");
		ZooKeeperServerMain.main(args);

	}

	@Override
	public void start() {
		init();

	}

	@SuppressWarnings("deprecation")
	@Override
	public void stop() {
		Thread.currentThread().stop();

	}

	@Override
	public void insertPersistent(String blockname, byte[] data) {
		// String blockname = "/foo";
		// String data = "pgaref";
		LOG.debug("pgaref: Create Internal Called from Cassandra add CommitLOG entry!");
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
			Requestdata.putInt(CreateMode.PERSISTENT.toFlag());
			Requestdata.flip();
		} catch (IOException ex) {
			LOG.error("pgaref - Exception Serializing ACL List");
		} catch (BufferOverflowException ex) {
			LOG.error("BufferOverflowException: " + ex);
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
		i++;
		try {
			ZooKeeperServer.finalProcessor.processRequest(req);
		} catch (RequestProcessorException e) {
			LOG.debug("pgaref request error" + e);
		}
		LOG.info("Fake-Requestis going to process!!!");

	}

	@Override
	public void delete(String blockname) {
		// TODO Auto-generated method stub
	}

	@Override
	public void memoryCleanup() {
		// TODO Auto-generated method stub

	}
	
	@Override
	public String getServerState() {
		return "StandAlone";
	}
	
	public static void main(String[] args) {
		LOG.info("Standalone Test Started");
	//	Thread test = new Thread(new ZooStandalone());
	//	test.start();
	}

	@Override
	public void run() {
		init();
		
	}

	

}
