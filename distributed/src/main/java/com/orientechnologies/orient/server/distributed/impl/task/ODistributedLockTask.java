/*
 *
 *  *  Copyright 2014 Orient Technologies LTD (info(at)orientechnologies.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://www.orientechnologies.com
 *
 */
package com.orientechnologies.orient.server.distributed.impl.task;

import com.orientechnologies.orient.core.command.OCommandDistributedReplicateRequest;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.distributed.ODistributedRequestId;
import com.orientechnologies.orient.server.distributed.ODistributedServerManager;
import com.orientechnologies.orient.server.distributed.ORemoteTaskFactory;
import com.orientechnologies.orient.server.distributed.task.OAbstractReplicatedTask;
import com.orientechnologies.orient.server.distributed.task.ODistributedOperationException;
import com.orientechnologies.orient.server.distributed.task.ORemoteTask;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

/**
 * Task to acquire and release a distributed exclusive lock across the entire cluster. In case the server is declared unreachable,
 * the lock is freed.
 *
 * @author Luca Garulli (l.garulli--at--orientdb.com)
 */
public class ODistributedLockTask extends OAbstractReplicatedTask {
  public static final int FACTORYID = 26;

  private String  resource;
  private long    timeout;
  private boolean acquire;
  private String  lockManagerServer;

  public ODistributedLockTask() {
  }

  public ODistributedLockTask(final String lockManagerServer, final String resource, final long timeout, final boolean acquire) {
    this.lockManagerServer = lockManagerServer;
    this.resource = resource;
    this.timeout = timeout;
    this.acquire = acquire;
  }

  @Override
  public Object execute(final ODistributedRequestId msgId, final OServer iServer, ODistributedServerManager iManager,
      final ODatabaseDocumentInternal database) throws Exception {

    if (acquire)
      iManager.getLockManagerExecutor().acquireExclusiveLock(resource, getNodeSource(), timeout);
    else
      iManager.getLockManagerExecutor().releaseExclusiveLock(resource, getNodeSource());

    return true;
  }

  @Override
  public ORemoteTask getUndoTask(ODistributedServerManager dManager, final ODistributedRequestId reqId, List<String> servers) {
    if (acquire)
      // RELEASE
      return new ODistributedLockTask(lockManagerServer, resource, timeout, false);

    return null;
  }

  @Override
  public int[] getPartitionKey() {
    // RELEASE MUST TO ALWAYS TO THE SERVICE QUEUE
    return acquire ? LOCK : FAST_NOLOCK;
  }

  @Override
  public OCommandDistributedReplicateRequest.QUORUM_TYPE getQuorumType() {
    return OCommandDistributedReplicateRequest.QUORUM_TYPE.ALL;
  }

  @Override
  public RESULT_STRATEGY getResultStrategy() {
    return RESULT_STRATEGY.ANY;
  }

  @Override
  public boolean isUsingDatabase() {
    return false;
  }

  @Override
  public void toStream(final DataOutput out) throws IOException {
    out.writeUTF(resource);
    out.writeBoolean(acquire);
    out.writeLong(timeout);
  }

  @Override
  public void fromStream(final DataInput in, final ORemoteTaskFactory factory) throws IOException {
    resource = in.readUTF();
    acquire = in.readBoolean();
    timeout = in.readLong();
  }

  @Override
  public boolean isNodeOnlineRequired() {
    return false;
  }

  @Override
  public void checkIsValid(final ODistributedServerManager dManager) {
    // CHECKS THE LOCK MANAGER SERVER IS STILL AVAILABLE
    if (!dManager.isNodeAvailable(dManager.getLockManagerServer()))
      throw new ODistributedOperationException("Lock Manager server changed during lock " + (acquire ? "acquire" : "release"));
  }

  @Override
  public long getDistributedTimeout() {
    return timeout > 0 ? timeout : OGlobalConfiguration.DISTRIBUTED_COMMAND_LONG_TASK_SYNCH_TIMEOUT.getValueAsLong();
  }

  @Override
  public String getName() {
    return "exc_lock";
  }

  @Override
  public int getFactoryId() {
    return FACTORYID;
  }

  @Override
  public String toString() {
    return getName() + " " + (acquire ? "acquire" : "release") + " resource=" + resource;
  }
}
