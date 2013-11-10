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
import org.apache.cassandra.config.KSMetaData;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.RowMutation;
import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.service.MigrationManager;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.Pair;
import org.apache.cassandra.utils.WrappedRunnable;
import org.cliffc.high_scale_lib.NonBlockingHashSet;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Ordering;

public class MyRowMutationReplayer {
	private static final Logger logger = LoggerFactory
			.getLogger(MyRowMutationReplayer.class);
	private static final int MAX_OUTSTANDING_REPLAY_COUNT = 1024;

	private final Set<Keyspace> keyspacesRecovered;
	private final List<Future<?>> futures;
	private final Map<UUID, AtomicInteger> invalidMutations;
	private final AtomicInteger replayedCount;
	private final Map<UUID, ReplayPosition> cfPositions;
	private final ReplayPosition globalPosition;

	public MyRowMutationReplayer() {
		this.keyspacesRecovered = new NonBlockingHashSet<Keyspace>();
		this.futures = new ArrayList<Future<?>>();
		this.invalidMutations = new HashMap<UUID, AtomicInteger>();
		// count the number of replayed mutation. We don't really care about
		// atomicity, but we need it to be a reference.
		this.replayedCount = new AtomicInteger();

		// compute per-CF and global replay positions
		cfPositions = new HashMap<UUID, ReplayPosition>();
		Ordering<ReplayPosition> replayPositionOrdering = Ordering
				.from(ReplayPosition.comparator);
		Map<UUID, Pair<ReplayPosition, Long>> truncationPositions = SystemKeyspace
				.getTruncationRecords();
		for (ColumnFamilyStore cfs : ColumnFamilyStore.all()) {
			// it's important to call RP.gRP per-cf, before aggregating all the
			// positions w/ the Ordering.min call
			// below: gRP will return NONE if there are no flushed sstables,
			// which is important to have in the
			// list (otherwise we'll just start replay from the first flush
			// position that we do have, which is not correct).
			ReplayPosition rp = ReplayPosition.getReplayPosition(cfs
					.getSSTables());

			// but, if we've truncted the cf in question, then we need to need
			// to start replay after the truncation
			Pair<ReplayPosition, Long> truncateRecord = truncationPositions
					.get(cfs.metadata.cfId);
			ReplayPosition truncatedAt = truncateRecord == null ? null
					: truncateRecord.left;
			if (truncatedAt != null)
				rp = replayPositionOrdering.max(Arrays.asList(rp, truncatedAt));

			cfPositions.put(cfs.metadata.cfId, rp);
		}
		globalPosition = replayPositionOrdering.min(cfPositions.values());
		logger.info("Global replay position is {} from columnfamilies {}",
				globalPosition, FBUtilities.toString(cfPositions));
	}

	public int blockForWrites() {
		for (Map.Entry<UUID, AtomicInteger> entry : invalidMutations.entrySet())
			logger.info(String
					.format("Skipped %d mutations from unknown (probably removed) CF with id %s",
							entry.getValue().intValue(), entry.getKey()));

		// wait for all the writes to finish on the mutation stage
		FBUtilities.waitOnFutures(futures);
		logger.debug("Finished waiting on mutations from recovery");

		// flush replayed keyspaces
		futures.clear();
		for (Keyspace keyspace : keyspacesRecovered)
			futures.addAll(keyspace.flush());
		FBUtilities.waitOnFutures(futures);
		return replayedCount.get();
	}

	public void recover(RowMutation rm) throws IOException {
		logger.info("pgaref - Replaying {}", rm.toString());

		final RowMutation frm = rm;
		Runnable runnable = new WrappedRunnable() {
			public void runMayThrow() throws IOException {
				if (Schema.instance.getKSMetaData(frm.getKeyspaceName()) == null)
					return;

				final Keyspace keyspace = Keyspace.open(frm.getKeyspaceName());

				// Rebuild the row mutation, omitting column families that
				// a) have already been flushed,
				// b) are part of a cf that was dropped. Keep in mind that the
				// cf.name() is suspect. do every thing based on the cfid
				// instead.
				RowMutation newRm = null;
				for (ColumnFamily columnFamily : frm.getColumnFamilies()) {
					if (Schema.instance.getCF(columnFamily.id()) == null)
						// null means the cf has been dropped
						continue;

					// replay if current segment is newer than last flushed one
					// or,
					// if it is the last known segment, if we are after the
					// replay position
					/*
					 * pgaref We assume that the segment is always NEW!!!! if
					 * (segment > rp.segment || (segment == rp.segment &&
					 * entryLocation > rp.position)) {
					 */
					if (newRm == null)
						newRm = new RowMutation(frm.getKeyspaceName(),
								frm.key());
					newRm.add(columnFamily);
					replayedCount.incrementAndGet();

				}
				if (newRm != null) {
					assert !newRm.isEmpty();
					Keyspace.open(newRm.getKeyspaceName()).apply(newRm, false, true);
					keyspacesRecovered.add(keyspace);
					KSMetaData ksm = Schema.instance.getKSMetaData(keyspace.getName());
					try {
						MigrationManager.announceNewKeyspace(ksm);
					} catch (ConfigurationException e) {
						logger.info("pgarer -FAILED TO ANNOUCE NEW SCHEMA");
					}
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
