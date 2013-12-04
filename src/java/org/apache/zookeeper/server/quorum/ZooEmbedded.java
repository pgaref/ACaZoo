package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.KeeperException.NoNodeException;

public interface ZooEmbedded {
	
	/* Create Configuration Files and Classes */
	public void init();

	/* Starts Server thread (call init internally) */
	public void start();

	/* Stops thread */
	public void stop();
	
	/* Inserts a internal block with fake session and socket connection to avoid overhead 
	 * this block by default is persistent sequential with ACL = no security 
	 */
	public void insertPersistent(String blockname, byte[] data);
	
	/* Delete a spefic znode */
	public void delete(String blockname,long zxid) throws NoNodeException;
	
	/* Periodic cleanup of znodes  to avoid memory FULL */
	public void memoryCleanup();
	
	/* Get Server State : 
	 * For one server: Standalone 
	 * For quorum: Following or Leading
	 */
	public String getServerState();
	
	/*
	 * Custom Elections For AcaZoo Compaction Scheduling!
	 * 
	 */
	
	public void TrigerRandomElections();
	
	
	public void TrigerRoundRobbinElection();

}
