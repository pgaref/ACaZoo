package org.apache.cassandra.db.commitlog;

/*
 * pgaref's Master Thesis is not as easy as expected
 * Please use at you own risk! Be patient and optimistic
 *
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.cassandra.concurrent.Stage;
import org.apache.cassandra.concurrent.StageManager;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.DefsTables;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.RowMutation;
import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.Pair;
import org.apache.cassandra.utils.WrappedRunnable;
import org.cliffc.high_scale_lib.NonBlockingHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Ordering;

public class MyRowMutationReplayer {
	private static final Logger logger = LoggerFactory
			.getLogger(MyRowMutationReplayer.class);
	private static final int MAX_OUTSTANDING_REPLAY_COUNT = 1024;

	private final Set<Keyspace> keyspacesRecovered;
	private final List<Future<?>> futures;
	
	public MyRowMutationReplayer() {
		this.keyspacesRecovered = new NonBlockingHashSet<Keyspace>();
		this.futures = new ArrayList<Future<?>>();

	}

	public int blockForWrites() {
		
		FBUtilities.waitOnFutures(futures);
		logger.debug("Finished waiting on mutations from recovery");
		futures.clear();
		for (Keyspace keyspace : keyspacesRecovered)
			futures.addAll(keyspace.flush());
		FBUtilities.waitOnFutures(futures);
		return futures.size();
	}

	public void recover(RowMutation rm) throws IOException {
		logger.info("pgaref - Replaying {}", rm.toString());

		final RowMutation frm = rm;
		Runnable runnable = new WrappedRunnable() {
			public void runMayThrow() throws IOException {
				
				final Keyspace keyspace = Keyspace.open(frm.getKeyspaceName());
				
				if (Schema.instance.getKSMetaData(frm.getKeyspaceName()) == null)
					return;

				// Rebuild the row mutation, omitting column families that
				// a) have already been flushed,
				// b) are part of a cf that was dropped. Keep in mind that the
				// cf.name() is suspect. do every thing based on the cfid
				// instead.
				RowMutation newRm = null;
				for (ColumnFamily columnFamily : frm.getColumnFamilies()) {
					if (Schema.instance.getCF(columnFamily.id()) == null){
						// null means the cf has been dropped
						continue;
					}
					// replay if current segment is newer than last flushed one
					// or,
					// if it is the last known segment, if we are after the
					// replay position
					if (newRm == null)
						newRm = new RowMutation(frm.getKeyspaceName(),
								frm.key());
					newRm.add(columnFamily);
				}
				if (newRm != null) {
					logger.debug("pgaref - MyRowMutaion Final Case");
					assert !newRm.isEmpty();
					try {
						DefsTables.mergeSchema(Arrays.asList(frm));
					} catch (ConfigurationException e) {
						logger.error("pgaref - Failed to Merge Schem for new RowMutation");
					}
					keyspacesRecovered.add(keyspace);
				}
			}
		};
		futures.add(StageManager.getStage(Stage.MUTATION).submit(runnable));
		if (futures.size() > MAX_OUTSTANDING_REPLAY_COUNT) {
			FBUtilities.waitOnFutures(futures);
			futures.clear();
		}

	}
}
